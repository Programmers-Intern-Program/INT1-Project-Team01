package back.domain.gateway.client.transport;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.exception.OpenClawGatewayException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenClawJavaWebSocketTransportTest {

    @Test
    @DisplayName("Java WebSocket transport는 요청을 전송하고 Gateway 응답을 handler로 전달한다")
    void sendAndReceive_mockWebSocket_success() throws Exception {
        // given
        MockWebServer server = new MockWebServer();
        LinkedBlockingQueue<String> receivedRequests = new LinkedBlockingQueue<>();
        AtomicReference<WebSocket> serverWebSocket = new AtomicReference<>();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                serverWebSocket.set(webSocket);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                receivedRequests.add(text);
            }
        }));
        server.start();

        LinkedBlockingQueue<OpenClawRpcResponse> responses = new LinkedBlockingQueue<>();
        OpenClawJavaWebSocketTransport transport = new OpenClawJavaWebSocketTransport(
                HttpClient.newHttpClient(),
                objectMapper(),
                new GatewayUrlNormalizer()
        );

        try {
            transport.connect(
                    new OpenClawGatewayConnectionContext(server.url("/gateway").toString(), "secret-token"),
                    responses::add,
                    exception -> {
                    }
            );

            // when
            transport.send(OpenClawRpcRequest.of("req-1", "agents.list", Map.of()));
            serverWebSocket.get().send("""
                    {"type":"res","id":"req-1","ok":true,"payload":{"agents":[]}}
                    """);

            // then
            assertThat(transport.isConnected()).isTrue();
            assertThat(receivedRequests.poll(1, TimeUnit.SECONDS))
                    .contains("\"id\":\"req-1\"")
                    .contains("\"method\":\"agents.list\"");
            assertThat(responses.poll(1, TimeUnit.SECONDS).id()).isEqualTo("req-1");
        } finally {
            transport.close();
            WebSocket webSocket = serverWebSocket.get();
            if (webSocket != null) {
                webSocket.close(1000, "test completed");
            }
            server.shutdown();
        }
    }

    @Test
    @DisplayName("WebSocket 연결 생성이 멈추면 connect timeout으로 실패한다")
    void connect_hangingWebSocketBuild_failsWithConnectionError() {
        // given
        Duration connectTimeout = Duration.ofMillis(20);
        FakeHttpClient httpClient = new FakeHttpClient(new CompletableFuture<>());
        OpenClawJavaWebSocketTransport transport = new OpenClawJavaWebSocketTransport(
                httpClient,
                objectMapper(),
                new GatewayUrlNormalizer(),
                connectTimeout
        );

        // when & then
        assertThatThrownBy(() -> transport.connect(
                new OpenClawGatewayConnectionContext("ws://localhost:3999/gateway", "secret-token"),
                response -> {
                },
                exception -> {
                }
        ))
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("gateway_connection_failed");
        assertThat(httpClient.webSocketConnectTimeout).isEqualTo(connectTimeout);
    }

    @Test
    @DisplayName("socket 오류는 조립 중이던 partial message buffer를 비운다")
    void onError_afterPartialText_clearsMessageBuffer() throws Exception {
        // given
        FakeWebSocket webSocket = new FakeWebSocket();
        FakeHttpClient httpClient = new FakeHttpClient(CompletableFuture.completedFuture(webSocket));
        LinkedBlockingQueue<OpenClawRpcResponse> responses = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<OpenClawGatewayException> failures = new LinkedBlockingQueue<>();
        OpenClawJavaWebSocketTransport transport = new OpenClawJavaWebSocketTransport(
                httpClient,
                objectMapper(),
                new GatewayUrlNormalizer(),
                Duration.ofSeconds(1)
        );
        transport.connect(
                new OpenClawGatewayConnectionContext("ws://localhost:3999/gateway", "secret-token"),
                responses::add,
                failures::add
        );
        java.net.http.WebSocket.Listener listener = httpClient.listener;

        // when
        listener.onOpen(webSocket);
        listener.onText(webSocket, "{\"type\":\"res\"", false)
                .toCompletableFuture()
                .get(100, TimeUnit.MILLISECONDS);
        listener.onError(webSocket, new IllegalStateException("closed"));
        assertThat(failures.poll(1, TimeUnit.SECONDS).gatewayErrorCode())
                .isEqualTo("gateway_disconnected");

        listener.onOpen(webSocket);
        listener.onText(webSocket, "{\"type\":\"res\",\"id\":\"req-1\",\"ok\":true,\"payload\":{}}", true)
                .toCompletableFuture()
                .get(100, TimeUnit.MILLISECONDS);

        // then
        OpenClawRpcResponse response = responses.poll(1, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("req-1");
        assertThat(failures.poll(100, TimeUnit.MILLISECONDS)).isNull();
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static class FakeHttpClient extends HttpClient {

        private final HttpClient delegate = HttpClient.newHttpClient();
        private final CompletableFuture<java.net.http.WebSocket> webSocketFuture;
        private java.net.http.WebSocket.Listener listener;
        private Duration webSocketConnectTimeout;

        FakeHttpClient(CompletableFuture<java.net.http.WebSocket> webSocketFuture) {
            this.webSocketFuture = webSocketFuture;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }

        @Override
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) throws IOException, InterruptedException {
            return delegate.send(request, responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return delegate.sendAsync(request, responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler);
        }

        @Override
        public java.net.http.WebSocket.Builder newWebSocketBuilder() {
            return new FakeWebSocketBuilder();
        }

        private class FakeWebSocketBuilder implements java.net.http.WebSocket.Builder {

            @Override
            public java.net.http.WebSocket.Builder header(String name, String value) {
                return this;
            }

            @Override
            public java.net.http.WebSocket.Builder connectTimeout(Duration timeout) {
                webSocketConnectTimeout = timeout;
                return this;
            }

            @Override
            public java.net.http.WebSocket.Builder subprotocols(
                    String mostPreferred,
                    String... lesserPreferred
            ) {
                return this;
            }

            @Override
            public CompletableFuture<java.net.http.WebSocket> buildAsync(
                    URI uri,
                    java.net.http.WebSocket.Listener listener
            ) {
                FakeHttpClient.this.listener = listener;
                return webSocketFuture;
            }
        }
    }

    private static class FakeWebSocket implements java.net.http.WebSocket {

        @Override
        public CompletableFuture<java.net.http.WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<java.net.http.WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<java.net.http.WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<java.net.http.WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<java.net.http.WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long count) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
        }
    }
}
