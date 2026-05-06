package back.domain.gateway.client;

import back.domain.gateway.client.rpc.OpenClawPendingRequests;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.client.transport.GatewayUrlNormalizer;
import back.domain.gateway.client.transport.OpenClawGatewayTransport;
import back.domain.gateway.client.transport.OpenClawJavaWebSocketTransport;
import back.domain.gateway.exception.OpenClawGatewayException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class OpenClawGatewayRpcClient implements OpenClawGatewayClient {

    private final OpenClawGatewayTransport transport;
    private final OpenClawPendingRequests pendingRequests;
    private final Supplier<String> requestIdSupplier;
    private final Duration rpcTimeout;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Gateway client composes injected transport and pending request components."
    )
    public OpenClawGatewayRpcClient(
            OpenClawGatewayTransport transport,
            OpenClawPendingRequests pendingRequests,
            Supplier<String> requestIdSupplier,
            Duration rpcTimeout
    ) {
        this.transport = Objects.requireNonNull(transport);
        this.pendingRequests = Objects.requireNonNull(pendingRequests);
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier);
        this.rpcTimeout = Objects.requireNonNull(rpcTimeout);
    }

    public static OpenClawGatewayRpcClient webSocket(Duration rpcTimeout) {
        Duration timeout = Objects.requireNonNull(rpcTimeout);
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new OpenClawGatewayRpcClient(
                new OpenClawJavaWebSocketTransport(
                        HttpClient.newBuilder()
                                .connectTimeout(timeout)
                                .build(),
                        objectMapper,
                        new GatewayUrlNormalizer(),
                        timeout
                ),
                new OpenClawPendingRequests(Executors.newSingleThreadScheduledExecutor()),
                () -> UUID.randomUUID().toString(),
                timeout
        );
    }

    @Override
    public void connect(OpenClawGatewayConnectionContext context) {
        transport.connect(context, this::handleResponse, this::handleFailure);
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
    public void close() {
        pendingRequests.failAll(OpenClawGatewayException.gatewayDisconnected());
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

    private void handleResponse(OpenClawRpcResponse response) {
        pendingRequests.complete(response);
    }

    private void handleFailure(OpenClawGatewayException exception) {
        pendingRequests.failAll(exception);
    }

    private Optional<OpenClawAgentSummary> toAgentSummary(Map<?, ?> agent) {
        String agentId = firstString(agent, "agentId", "id");
        String name = firstString(agent, "name");
        if (agentId == null || name == null) {
            return Optional.empty();
        }
        return Optional.of(new OpenClawAgentSummary(agentId, name));
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
}
