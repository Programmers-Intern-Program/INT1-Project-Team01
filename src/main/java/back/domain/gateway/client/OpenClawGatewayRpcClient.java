package back.domain.gateway.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
    private static final String CONNECT_CHALLENGE_EVENT = "connect.challenge";
    private static final String CHAT_SEND_METHOD = "chat.send";
    private static final Duration DEFAULT_CHAT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration DEFAULT_REMOTE_CONNECT_CHALLENGE_WAIT = Duration.ofMillis(750);
    private static final int PROTOCOL_MIN = 3;
    private static final int PROTOCOL_MAX = 3;
    private static final String CLIENT_ID = "gateway-client";
    private static final String CLIENT_VERSION = "1.0.0";
    private static final String CLIENT_PLATFORM = "java";
    private static final String CLIENT_MODE = "backend";
    private static final String OPERATOR_ROLE = "operator";
    private static final String OPERATOR_READ_SCOPE = "operator.read";
    private static final String OPERATOR_WRITE_SCOPE = "operator.write";
    private static final String OPERATOR_ADMIN_SCOPE = "operator.admin";
    // agents.create and agents.files.set are admin-scoped Gateway RPCs.
    private static final List<String> OPERATOR_SCOPES =
            List.of(OPERATOR_READ_SCOPE, OPERATOR_WRITE_SCOPE, OPERATOR_ADMIN_SCOPE);

    private final OpenClawGatewayTransport transport;
    private final OpenClawPendingRequests pendingRequests;
    private final Supplier<String> requestIdSupplier;
    private final Duration rpcTimeout;
    private final Duration chatTimeout;
    private final Duration remoteConnectChallengeWait;
    private final OpenClawGatewayDeviceAuthenticator deviceAuthenticator;
    private final ConcurrentHashMap<String, PendingChatStream> pendingChatsBySessionKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingChatSessionKeyByRequestId = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Map<String, Object>> connectChallenge = new CompletableFuture<>();

    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
            justification = "Gateway client validates constructor dependencies and has no finalizer.")
    public OpenClawGatewayRpcClient(
            OpenClawGatewayTransport transport,
            OpenClawPendingRequests pendingRequests,
            Supplier<String> requestIdSupplier,
            Duration rpcTimeout) {
        this(
                transport,
                pendingRequests,
                requestIdSupplier,
                rpcTimeout,
                DEFAULT_CHAT_TIMEOUT,
                OpenClawGatewayDeviceAuthenticator.defaultAuthenticator(),
                Duration.ZERO);
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
        this(
                transport,
                pendingRequests,
                requestIdSupplier,
                rpcTimeout,
                chatTimeout,
                OpenClawGatewayDeviceAuthenticator.defaultAuthenticator(),
                Duration.ZERO);
    }

    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
            justification = "Gateway client validates constructor dependencies and has no finalizer.")
    OpenClawGatewayRpcClient(
            OpenClawGatewayTransport transport,
            OpenClawPendingRequests pendingRequests,
            Supplier<String> requestIdSupplier,
            Duration rpcTimeout,
            Duration chatTimeout,
            OpenClawGatewayDeviceAuthenticator deviceAuthenticator) {
        this(
                transport,
                pendingRequests,
                requestIdSupplier,
                rpcTimeout,
                chatTimeout,
                deviceAuthenticator,
                Duration.ZERO);
    }

    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
            justification = "Gateway client validates constructor dependencies and has no finalizer.")
    OpenClawGatewayRpcClient(
            OpenClawGatewayTransport transport,
            OpenClawPendingRequests pendingRequests,
            Supplier<String> requestIdSupplier,
            Duration rpcTimeout,
            Duration chatTimeout,
            OpenClawGatewayDeviceAuthenticator deviceAuthenticator,
            Duration remoteConnectChallengeWait) {
        this.transport = Objects.requireNonNull(transport);
        this.pendingRequests = Objects.requireNonNull(pendingRequests);
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier);
        this.rpcTimeout = Objects.requireNonNull(rpcTimeout);
        this.chatTimeout = requirePositive(chatTimeout, "chatTimeout");
        this.deviceAuthenticator = Objects.requireNonNull(deviceAuthenticator);
        this.remoteConnectChallengeWait = requireNonNegative(remoteConnectChallengeWait, "remoteConnectChallengeWait");
    }

    public static OpenClawGatewayRpcClient webSocket(Duration rpcTimeout) {
        return webSocket(rpcTimeout, OpenClawGatewayDeviceIdentityStore.defaultDirectory());
    }

    static OpenClawGatewayRpcClient webSocket(Duration rpcTimeout, java.nio.file.Path deviceIdentityDirectory) {
        return webSocket(rpcTimeout, deviceIdentityDirectory, DEFAULT_REMOTE_CONNECT_CHALLENGE_WAIT);
    }

    static OpenClawGatewayRpcClient webSocket(
            Duration rpcTimeout,
            java.nio.file.Path deviceIdentityDirectory,
            Duration remoteConnectChallengeWait) {
        Duration timeout = Objects.requireNonNull(rpcTimeout);
        OpenClawGatewayDeviceAuthenticator authenticator = new OpenClawGatewayDeviceAuthenticator(
                new OpenClawGatewayDeviceIdentityStore(deviceIdentityDirectory));
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
                timeout,
                DEFAULT_CHAT_TIMEOUT,
                authenticator,
                remoteConnectChallengeWait);
    }

    @Override
    public void connect(OpenClawGatewayConnectionContext context) {
        OpenClawGatewayConnectionContext connectionContext = Objects.requireNonNull(context);
        connectChallenge = new CompletableFuture<>();
        transport.connect(connectionContext, this::handleResponse, this::handleEvent, this::handleFailure);
        try {
            sendConnectHandshake(connectionContext);
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
    public void deleteAgent(String agentId, boolean deleteFiles) {
        sendRpc(
                "agents.delete",
                Map.of(
                        "agentId", requireNotBlank(agentId, "agentId"),
                        "deleteFiles", deleteFiles));
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

    private void sendConnect(OpenClawGatewayConnectionContext context, Optional<Map<String, Object>> challenge) {
        sendRpc(CONNECT_METHOD, connectParams(context, challenge));
    }

    private void sendConnectHandshake(OpenClawGatewayConnectionContext context) {
        Optional<Map<String, Object>> earlyChallenge = awaitConnectChallenge(context);
        if (earlyChallenge.isPresent()) {
            sendConnect(context, earlyChallenge);
            return;
        }
        sendConnectAndRetryOnChallenge(context);
    }

    private Optional<Map<String, Object>> awaitConnectChallenge(OpenClawGatewayConnectionContext context) {
        Optional<Map<String, Object>> completedChallenge = completedConnectChallenge();
        if (completedChallenge.isPresent() || !shouldWaitForConnectChallenge(context.gatewayUrl())) {
            return completedChallenge;
        }
        try {
            return Optional.of(connectChallenge.get(remoteConnectChallengeWait.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException exception) {
            throw OpenClawGatewayException.sendFailed(CONNECT_CHALLENGE_EVENT, null, exception);
        }
    }

    private boolean shouldWaitForConnectChallenge(String gatewayUrl) {
        return !remoteConnectChallengeWait.isZero() && !isLoopbackGateway(gatewayUrl);
    }

    private boolean isLoopbackGateway(String gatewayUrl) {
        try {
            String host = URI.create(gatewayUrl).getHost();
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || "0:0:0:0:0:0:0:1".equals(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void sendConnectAndRetryOnChallenge(OpenClawGatewayConnectionContext context) {
        String requestId = requestIdSupplier.get();
        var response = pendingRequests.register(requestId, CONNECT_METHOD, rpcTimeout);
        try {
            transport.send(OpenClawRpcRequest.of(requestId, CONNECT_METHOD, connectParams(context, Optional.empty())));
            ConnectHandshakeSignal signal = awaitConnectResponseOrChallenge(response);
            if (signal.challenge().isPresent()) {
                pendingRequests.cancel(requestId);
                sendConnect(context, signal.challenge());
            }
        } catch (CompletionException exception) {
            pendingRequests.cancel(requestId);
            if (exception.getCause() instanceof OpenClawGatewayException gatewayException) {
                throw gatewayException;
            }
            throw OpenClawGatewayException.sendFailed(CONNECT_METHOD, requestId, exception);
        } catch (OpenClawGatewayException exception) {
            pendingRequests.cancel(requestId);
            throw exception;
        } catch (RuntimeException exception) {
            pendingRequests.cancel(requestId);
            throw OpenClawGatewayException.sendFailed(CONNECT_METHOD, requestId, exception);
        }
    }

    private ConnectHandshakeSignal awaitConnectResponseOrChallenge(
            CompletableFuture<Map<String, Object>> response) {
        CompletableFuture<ConnectHandshakeSignal> responseSignal =
                response.thenApply(ignored -> ConnectHandshakeSignal.connected());
        CompletableFuture<ConnectHandshakeSignal> challengeSignal =
                connectChallenge.thenApply(ConnectHandshakeSignal::challenge);
        return CompletableFuture.anyOf(responseSignal, challengeSignal)
                .thenApply(ConnectHandshakeSignal.class::cast)
                .join();
    }

    private Map<String, Object> connectParams(
            OpenClawGatewayConnectionContext context, Optional<Map<String, Object>> challenge) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("minProtocol", PROTOCOL_MIN);
        params.put("maxProtocol", PROTOCOL_MAX);
        params.put(
                "client",
                Map.of(
                        "id", CLIENT_ID,
                        "version", CLIENT_VERSION,
                        "platform", CLIENT_PLATFORM,
                        "mode", CLIENT_MODE));
        params.put("role", OPERATOR_ROLE);
        params.put("scopes", OPERATOR_SCOPES);
        params.put("auth", Map.of("token", context.token()));
        challenge.ifPresent(challengePayload -> params.put(
                "device",
                deviceAuthenticator.buildDeviceAuth(
                        context.gatewayUrl(),
                        context.token(),
                        challengePayload,
                        CLIENT_ID,
                        CLIENT_MODE,
                        OPERATOR_ROLE,
                        OPERATOR_SCOPES)));
        return Map.copyOf(params);
    }

    private Optional<Map<String, Object>> completedConnectChallenge() {
        if (!connectChallenge.isDone()) {
            return Optional.empty();
        }
        try {
            return Optional.of(connectChallenge.join());
        } catch (CompletionException exception) {
            return Optional.empty();
        }
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
        if (CONNECT_CHALLENGE_EVENT.equalsIgnoreCase(event.event())) {
            connectChallenge.complete(event.payload());
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

    private static Duration requireNonNegative(Duration timeout, String fieldName) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be zero or positive");
        }
        return timeout;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
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

    private record ConnectHandshakeSignal(Optional<Map<String, Object>> challenge) {

        private static ConnectHandshakeSignal connected() {
            return new ConnectHandshakeSignal(Optional.empty());
        }

        private static ConnectHandshakeSignal challenge(Map<String, Object> challenge) {
            return new ConnectHandshakeSignal(Optional.of(challenge));
        }
    }
}
