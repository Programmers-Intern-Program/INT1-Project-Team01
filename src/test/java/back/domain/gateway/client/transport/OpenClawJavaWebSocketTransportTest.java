package back.domain.gateway.client.transport;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
