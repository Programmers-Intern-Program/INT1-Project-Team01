package back.domain.gateway.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import back.domain.gateway.client.rpc.OpenClawPendingRequests;
import back.domain.gateway.client.rpc.dto.OpenClawGatewayEvent;
import back.domain.gateway.client.rpc.dto.OpenClawRpcError;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.client.transport.GatewayUrlNormalizer;
import back.domain.gateway.client.transport.OpenClawGatewayTransport;
import back.domain.gateway.client.transport.OpenClawJavaWebSocketTransport;
import back.domain.gateway.exception.OpenClawGatewayException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class OpenClawGatewayRpcClient implements OpenClawGatewayClient {

    private static final String CONNECT_METHOD = "connect";
    private static final String CHAT_SEND_METHOD = "chat.send";
    private static final Duration DEFAULT_CHAT_TIMEOUT = Duration.ofMinutes(3);
    private static final int PROTOCOL_MIN = 3;
    private static final int PROTOCOL_MAX = 3;

    private final OpenClawGatewayTransport transport;
    private final OpenClawPendingRequests pendingRequests;
    private final Supplier<String> requestIdSupplier;
    private final Duration rpcTimeout;
    private final Duration chatTimeout;
    private final ConcurrentHashMap<String, PendingChatStream> pendingChatsBySessionKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingChatSessionKeyByRequestId = new ConcurrentHashMap<>();

    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
            justification = "Gateway client validates constructor dependencies and has no finalizer.")
    public OpenClawGatewayRpcClient(
            OpenClawGatewayTransport transport,
            OpenClawPendingRequests pendingRequests,
            Supplier<String> requestIdSupplier,
            Duration rpcTimeout) {
        this(transport, pendingRequests, requestIdSupplier, rpcTimeout, DEFAULT_CHAT_TIMEOUT);
    }

    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
            justification = "Gateway client validates constructor dependencies and has no finalizer.")
    OpenClawGatewayRpcClient(
            OpenClawGatewayTransport transport,
            OpenClawPendingRequests pendingRequests,
            Supplier<String> requestIdSupplier,
            Duration rpcTimeout,
            Duration chatTimeout) {
        this.transport = Objects.requireNonNull(transport);
        this.pendingRequests = Objects.requireNonNull(pendingRequests);
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier);
        this.rpcTimeout = Objects.requireNonNull(rpcTimeout);
        this.chatTimeout = requirePositive(chatTimeout, "chatTimeout");
    }

    public static OpenClawGatewayRpcClient webSocket(Duration rpcTimeout) {
        Duration timeout = Objects.requireNonNull(rpcTimeout);
        ObjectMapper objectMapper =
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new OpenClawGatewayRpcClient(
                new OpenClawJavaWebSocketTransport(
                        HttpClient.newBuilder().connectTimeout(timeout).build(),
                        objectMapper,
                        new GatewayUrlNormalizer(),
                        timeout),
                new OpenClawPendingRequests(Executors.newSingleThreadScheduledExecutor()),
                () -> UUID.randomUUID().toString(),
                timeout);
    }

    @Override
    public void connect(OpenClawGatewayConnectionContext context) {
        transport.connect(context, this::handleResponse, this::handleEvent, this::handleFailure);
        try {
            sendConnect(context);
        } catch (RuntimeException exception) {
            transport.close();
            throw exception;
        }
    }

    @Override
    public List<OpenClawAgentSummary> listAgents() {
        Map<String, Object> payload = sendRpc("agents.list", Map.of());
        Object agents = payload.get("agents");
        if (!(agents instanceof List<?> agentItems)) {
            return List.of();
        }

        return agentItems.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::toAgentSummary)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public OpenClawAgentSummary createAgent(OpenClawAgentCreateCommand command) {
        Objects.requireNonNull(command);
        Map<String, Object> payload = sendRpc("agents.create", agentCreateParams(command));
        Map<?, ?> agentPayload = firstMap(payload, "agent").orElse(payload);
        String agentId = firstString(agentPayload, "agentId", "id");
        String name = firstString(agentPayload, "name");
        if (name == null) {
            name = command.name();
        }
        if (agentId == null) {
            throw OpenClawGatewayException.responseParseFailed(
                    new IllegalStateException("agents.create response has no agent id"));
        }
        return new OpenClawAgentSummary(agentId, name);
    }

    @Override
    public void setAgentFile(OpenClawAgentFileCommand command) {
        Objects.requireNonNull(command);
        sendRpc(
                "agents.files.set",
                Map.of(
                        "agentId", command.agentId(),
                        "name", command.name(),
                        "content", command.content()));
    }

    @Override
    public OpenClawChatResult sendChat(OpenClawChatCommand command) {
        Objects.requireNonNull(command);
        if (!transport.isConnected()) {
            throw OpenClawGatewayException.gatewayDisconnected();
        }

        String sessionKey = command.fullSessionKey();
        String requestId = requestIdSupplier.get();
        PendingChatStream chatStream = registerChatStream(requestId, sessionKey);
        try {
            transport.send(OpenClawRpcRequest.of(
                    requestId,
                    CHAT_SEND_METHOD,
                    Map.of(
                            "sessionKey", sessionKey,
                            "message", command.message(),
                            "idempotencyKey", command.idempotencyKey())));
            return chatStream.join();
        } catch (CompletionException exception) {
            removePendingChat(chatStream);
            if (exception.getCause() instanceof TimeoutException) {
                throw OpenClawGatewayException.rpcTimeout(CHAT_SEND_METHOD, requestId);
            }
            if (exception.getCause() instanceof OpenClawGatewayException gatewayException) {
                throw gatewayException;
            }
            throw OpenClawGatewayException.sendFailed(CHAT_SEND_METHOD, requestId, exception);
        } catch (OpenClawGatewayException exception) {
            removePendingChat(chatStream);
            throw exception;
        } catch (RuntimeException exception) {
            removePendingChat(chatStream);
            throw OpenClawGatewayException.sendFailed(CHAT_SEND_METHOD, requestId, exception);
        }
    }

    @Override
    public void close() {
        OpenClawGatewayException disconnected = OpenClawGatewayException.gatewayDisconnected();
        pendingRequests.failAll(disconnected);
        failAllPendingChats(disconnected);
        transport.close();
        pendingRequests.close();
    }

    private Map<String, Object> sendRpc(String method, Map<String, Object> params) {
        if (!transport.isConnected()) {
            throw OpenClawGatewayException.gatewayDisconnected();
        }

        String requestId = requestIdSupplier.get();
        var response = pendingRequests.register(requestId, method, rpcTimeout);
        try {
            transport.send(OpenClawRpcRequest.of(requestId, method, params));
            return response.join();
        } catch (CompletionException exception) {
            pendingRequests.cancel(requestId);
            if (exception.getCause() instanceof OpenClawGatewayException gatewayException) {
                throw gatewayException;
            }
            throw OpenClawGatewayException.sendFailed(method, requestId, exception);
        } catch (OpenClawGatewayException exception) {
            pendingRequests.cancel(requestId);
            throw exception;
        } catch (RuntimeException exception) {
            pendingRequests.cancel(requestId);
            throw OpenClawGatewayException.sendFailed(method, requestId, exception);
        }
    }

    private void sendConnect(OpenClawGatewayConnectionContext context) {
        sendRpc(CONNECT_METHOD, connectParams(context));
    }

    private Map<String, Object> connectParams(OpenClawGatewayConnectionContext context) {
        return Map.of(
                "minProtocol",
                PROTOCOL_MIN,
                "maxProtocol",
                PROTOCOL_MAX,
                "client",
                Map.of(
                        "id", "gateway-client",
                        "version", "1.0.0",
                        "platform", "java",
                        "mode", "backend"),
                "role",
                "operator",
                "scopes",
                List.of("operator.read", "operator.write", "operator.admin"),
                "auth",
                Map.of("token", context.token()));
    }

    private void handleResponse(OpenClawRpcResponse response) {
        if (completePendingChatFromResponse(response)) {
            return;
        }
        pendingRequests.complete(response);
    }

    private void handleEvent(OpenClawGatewayEvent event) {
        if (event == null || event.event() == null) {
            return;
        }
        if ("agent".equalsIgnoreCase(event.event())) {
            appendChatDelta(event.payload());
            return;
        }
        if ("chat".equalsIgnoreCase(event.event())) {
            completeChatFromEvent(event.payload());
        }
    }

    private void handleFailure(OpenClawGatewayException exception) {
        pendingRequests.failAll(exception);
        failAllPendingChats(exception);
    }

    private PendingChatStream registerChatStream(String requestId, String sessionKey) {
        PendingChatStream chatStream = new PendingChatStream(requestId, sessionKey);
        PendingChatStream previous = pendingChatsBySessionKey.putIfAbsent(sessionKey, chatStream);
        if (previous != null) {
            throw OpenClawGatewayException.sendFailed(
                    CHAT_SEND_METHOD,
                    requestId,
                    new IllegalStateException("Duplicate pending chat sessionKey=" + sessionKey));
        }
        pendingChatSessionKeyByRequestId.put(requestId, sessionKey);
        chatStream.enableTimeout(chatTimeout);
        return chatStream;
    }

    private boolean completePendingChatFromResponse(OpenClawRpcResponse response) {
        String sessionKey = pendingChatSessionKeyByRequestId.get(response.id());
        if (sessionKey == null) {
            return false;
        }

        PendingChatStream chatStream = pendingChatsBySessionKey.get(sessionKey);
        if (chatStream == null) {
            pendingChatSessionKeyByRequestId.remove(response.id(), sessionKey);
            return true;
        }
        if (!response.ok()) {
            PendingChatStream removed = removePendingChat(response.id(), sessionKey);
            if (removed != null) {
                removed.fail(OpenClawGatewayException.fromRpcError(response.error(), response.id()));
            }
            return true;
        }

        Map<String, Object> payload = response.payload() == null ? Map.of() : response.payload();
        if (!hasFinalChatPayload(payload)) {
            return true;
        }

        PendingChatStream removed = removePendingChat(response.id(), sessionKey);
        if (removed != null) {
            removed.complete(chatResultFromPayload(removed, payload));
        }
        return true;
    }

    private void appendChatDelta(Map<String, Object> payload) {
        String sessionKey = firstString(payload, "sessionKey");
        if (sessionKey == null) {
            return;
        }
        PendingChatStream chatStream = pendingChatsBySessionKey.get(sessionKey);
        if (chatStream == null || !isAssistantStream(payload)) {
            return;
        }

        Map<?, ?> data = firstMap(payload, "data").orElse(payload);
        String delta = firstString(data, "delta", "text");
        if (delta != null) {
            chatStream.append(delta);
        }
    }

    private void completeChatFromEvent(Map<String, Object> payload) {
        String sessionKey = firstString(payload, "sessionKey");
        if (sessionKey == null) {
            return;
        }
        PendingChatStream chatStream = pendingChatsBySessionKey.get(sessionKey);
        if (chatStream == null) {
            return;
        }

        String state = firstString(payload, "state", "status");
        if (isFinalState(state)) {
            PendingChatStream removed = removePendingChat(chatStream);
            if (removed != null) {
                removed.complete(chatResultFromPayload(removed, payload));
            }
            return;
        }
        if (isFailedState(state)) {
            PendingChatStream removed = removePendingChat(chatStream);
            if (removed != null) {
                removed.fail(chatEventFailure(removed.requestId(), payload));
            }
        }
    }

    private OpenClawChatResult chatResultFromPayload(PendingChatStream chatStream, Map<?, ?> payload) {
        Map<?, ?> chatPayload = chatPayload(payload);
        String sessionKey = firstString(chatPayload, "sessionKey");
        if (sessionKey == null) {
            sessionKey = chatStream.sessionKey();
        }

        String finalText = chatStream.accumulatedText();
        if (finalText.isBlank()) {
            finalText = firstString(chatPayload, "finalText", "text", "message", "response");
        }
        if (finalText == null || finalText.isBlank()) {
            finalText = messageContentText(chatPayload);
        }
        return new OpenClawChatResult(sessionKey, finalText);
    }

    private boolean hasFinalChatPayload(Map<?, ?> payload) {
        Map<?, ?> chatPayload = chatPayload(payload);
        if (isFinalState(firstString(chatPayload, "state", "status"))) {
            return true;
        }
        if (firstString(chatPayload, "finalText", "text", "response") != null) {
            return true;
        }
        return messageContentText(chatPayload) != null;
    }

    private Map<?, ?> chatPayload(Map<?, ?> payload) {
        return firstMap(payload, "chat").or(() -> firstMap(payload, "result")).orElse(payload);
    }

    private String messageContentText(Map<?, ?> payload) {
        Optional<Map<?, ?>> message = firstMap(payload, "message");
        if (message.isEmpty()) {
            return null;
        }
        Object content = message.get().get("content");
        if (!(content instanceof List<?> items)) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (Object item : items) {
            if (item instanceof String text) {
                builder.append(text);
                continue;
            }
            if (item instanceof Map<?, ?> contentItem) {
                String text = firstString(contentItem, "text", "content");
                if (text != null) {
                    builder.append(text);
                }
            }
        }
        String text = builder.toString();
        return text.isBlank() ? null : text;
    }

    private boolean isAssistantStream(Map<String, Object> payload) {
        String stream = firstString(payload, "stream");
        return stream == null || "assistant".equalsIgnoreCase(stream);
    }

    private boolean isFinalState(String state) {
        return equalsAnyIgnoreCase(state, "final", "completed", "done");
    }

    private boolean isFailedState(String state) {
        return equalsAnyIgnoreCase(state, "error", "failed", "canceled");
    }

    private boolean equalsAnyIgnoreCase(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private OpenClawGatewayException chatEventFailure(String requestId, Map<String, Object> payload) {
        String message = firstString(payload, "error", "errorMessage", "message");
        if (message == null) {
            message = "OpenClaw chat event failed";
        }
        return OpenClawGatewayException.fromRpcError(
                new OpenClawRpcError("chat_event_failed", message, requestId, Map.of()),
                requestId);
    }

    private PendingChatStream removePendingChat(PendingChatStream chatStream) {
        return removePendingChat(chatStream.requestId(), chatStream.sessionKey());
    }

    private PendingChatStream removePendingChat(String requestId, String sessionKey) {
        pendingChatSessionKeyByRequestId.remove(requestId, sessionKey);
        PendingChatStream chatStream = pendingChatsBySessionKey.remove(sessionKey);
        return chatStream;
    }

    private void failAllPendingChats(OpenClawGatewayException exception) {
        pendingChatsBySessionKey.forEach((sessionKey, chatStream) -> {
            if (pendingChatsBySessionKey.remove(sessionKey, chatStream)) {
                pendingChatSessionKeyByRequestId.remove(chatStream.requestId(), sessionKey);
                chatStream.fail(exception);
            }
        });
    }

    private Optional<OpenClawAgentSummary> toAgentSummary(Map<?, ?> agent) {
        String agentId = firstString(agent, "agentId", "id");
        String name = firstString(agent, "name");
        if (agentId == null || name == null) {
            return Optional.empty();
        }
        return Optional.of(new OpenClawAgentSummary(agentId, name));
    }

    private Map<String, Object> agentCreateParams(OpenClawAgentCreateCommand command) {
        var params = new java.util.LinkedHashMap<String, Object>();
        params.put("name", command.name());
        if (command.workspace() != null) {
            params.put("workspace", command.workspace());
        }
        if (command.emoji() != null) {
            params.put("emoji", command.emoji());
        }
        return java.util.Collections.unmodifiableMap(params);
    }

    private Optional<Map<?, ?>> firstMap(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return Optional.of(mapValue);
        }
        return Optional.empty();
    }

    private String firstString(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private static Duration requirePositive(Duration timeout, String fieldName) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return timeout;
    }

    private static class PendingChatStream {

        private final String requestId;
        private final String sessionKey;
        private final CompletableFuture<OpenClawChatResult> future = new CompletableFuture<>();
        private final StringBuilder finalText = new StringBuilder();

        PendingChatStream(String requestId, String sessionKey) {
            this.requestId = Objects.requireNonNull(requestId);
            this.sessionKey = Objects.requireNonNull(sessionKey);
        }

        String requestId() {
            return requestId;
        }

        String sessionKey() {
            return sessionKey;
        }

        void enableTimeout(Duration timeout) {
            future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void append(String delta) {
            synchronized (finalText) {
                finalText.append(delta);
            }
        }

        String accumulatedText() {
            synchronized (finalText) {
                return finalText.toString();
            }
        }

        OpenClawChatResult join() {
            return future.join();
        }

        void complete(OpenClawChatResult result) {
            future.complete(result);
        }

        void fail(OpenClawGatewayException exception) {
            future.completeExceptionally(exception);
        }
    }
}
