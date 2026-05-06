package back.domain.gateway.client.transport;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.client.rpc.OpenClawRpcResponseHandler;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.exception.OpenClawGatewayException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class OpenClawJavaWebSocketTransport implements OpenClawGatewayTransport {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GatewayUrlNormalizer urlNormalizer;
    private final Duration connectTimeout;
    private final StringBuilder messageBuffer = new StringBuilder();

    private volatile WebSocket webSocket;
    private volatile OpenClawRpcResponseHandler responseHandler;
    private volatile Consumer<OpenClawGatewayException> failureHandler;
    private volatile boolean connected;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Transport intentionally uses shared HttpClient/ObjectMapper infrastructure."
    )
    public OpenClawJavaWebSocketTransport(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            GatewayUrlNormalizer urlNormalizer
    ) {
        this(httpClient, objectMapper, urlNormalizer, DEFAULT_CONNECT_TIMEOUT);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Transport intentionally uses shared HttpClient/ObjectMapper infrastructure."
    )
    public OpenClawJavaWebSocketTransport(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            GatewayUrlNormalizer urlNormalizer,
            Duration connectTimeout
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.urlNormalizer = Objects.requireNonNull(urlNormalizer);
        this.connectTimeout = requirePositive(connectTimeout);
    }

    @Override
    public void connect(
            OpenClawGatewayConnectionContext context,
            OpenClawRpcResponseHandler responseHandler,
            Consumer<OpenClawGatewayException> failureHandler
    ) {
        this.responseHandler = Objects.requireNonNull(responseHandler);
        this.failureHandler = Objects.requireNonNull(failureHandler);
        URI webSocketUri = urlNormalizer.toWebSocketUri(context.gatewayUrl());
        clearMessageBuffer();
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(connectTimeout)
                    .header("Authorization", "Bearer " + context.token())
                    .buildAsync(webSocketUri, new GatewayWebSocketListener())
                    .orTimeout(timeoutMillis(connectTimeout), TimeUnit.MILLISECONDS)
                    .join();
        } catch (RuntimeException exception) {
            throw OpenClawGatewayException.connectionFailed(exception);
        }
    }

    @Override
    public void send(OpenClawRpcRequest request) {
        if (!isConnected()) {
            throw OpenClawGatewayException.gatewayDisconnected();
        }
        try {
            webSocket.sendText(objectMapper.writeValueAsString(request), true).join();
        } catch (JsonProcessingException exception) {
            throw OpenClawGatewayException.sendFailed(
                    request.method(),
                    request.id(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw OpenClawGatewayException.sendFailed(
                    request.method(),
                    request.id(),
                    exception
            );
        }
    }

    @Override
    public boolean isConnected() {
        return connected && webSocket != null;
    }

    @Override
    public void close() {
        WebSocket current = webSocket;
        connected = false;
        webSocket = null;
        clearMessageBuffer();
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "client closed");
        }
    }

    private class GatewayWebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            connected = true;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (messageBuffer) {
                messageBuffer.append(data);
                if (last) {
                    handleTextMessage(messageBuffer.toString());
                    messageBuffer.setLength(0);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            clearMessageBuffer();
            failureHandler.accept(OpenClawGatewayException.gatewayDisconnected());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected = false;
            clearMessageBuffer();
            failureHandler.accept(OpenClawGatewayException.gatewayDisconnected());
        }
    }

    private static Duration requirePositive(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        return timeout;
    }

    private static long timeoutMillis(Duration timeout) {
        return Math.max(1L, timeout.toMillis());
    }

    private void clearMessageBuffer() {
        synchronized (messageBuffer) {
            messageBuffer.setLength(0);
        }
    }

    private void handleTextMessage(String text) {
        try {
            OpenClawRpcResponse response = objectMapper.readValue(text, OpenClawRpcResponse.class);
            if ("res".equals(response.type())) {
                responseHandler.handle(response);
            }
        } catch (JsonProcessingException exception) {
            failureHandler.accept(OpenClawGatewayException.responseParseFailed(exception));
        }
    }
}
