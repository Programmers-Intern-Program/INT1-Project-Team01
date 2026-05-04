package back.domain.gateway.client;

import back.domain.gateway.client.rpc.OpenClawPendingRequests;
import back.domain.gateway.client.rpc.OpenClawRpcResponseHandler;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.client.transport.OpenClawGatewayTransport;
import back.domain.gateway.exception.OpenClawGatewayException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenClawGatewayRpcClientTest {

    @Test
    @DisplayName("connect는 transport에 Gateway context와 response handler를 등록한다")
    void connect_validContext_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        OpenClawGatewayRpcClient client = newClient(transport);
        OpenClawGatewayConnectionContext context = new OpenClawGatewayConnectionContext(
                "ws://localhost:3999",
                "secret-token"
        );

        // when
        client.connect(context);

        // then
        assertThat(transport.connectedContext).isEqualTo(context);
        assertThat(transport.responseHandler).isNotNull();
        assertThat(transport.isConnected()).isTrue();

        client.close();
    }

    @Test
    @DisplayName("agents.list는 RPC 요청을 보내고 응답 payload를 Agent summary로 변환한다")
    void listAgents_successResponse_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> transport.respond(OpenClawRpcResponse.success(
                request.id(),
                Map.of("agents", List.of(
                        Map.of("id", "agent-1", "name", "Backend Agent"),
                        Map.of("agentId", "agent-2", "name", "Reviewer Agent")
                ))
        ));
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        List<OpenClawAgentSummary> agents = client.listAgents();

        // then
        assertThat(transport.sentRequests).hasSize(1);
        assertThat(transport.sentRequests.getFirst().method()).isEqualTo("agents.list");
        assertThat(agents).containsExactly(
                new OpenClawAgentSummary("agent-1", "Backend Agent"),
                new OpenClawAgentSummary("agent-2", "Reviewer Agent")
        );

        client.close();
    }

    @Test
    @DisplayName("연결되지 않은 상태에서 agents.list를 호출하면 Gateway 예외가 발생한다")
    void listAgents_disconnected_throwsException() {
        // given
        OpenClawGatewayRpcClient client = newClient(new FakeGatewayTransport());

        // when & then
        assertThatThrownBy(client::listAgents)
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("gateway_disconnected");

        client.close();
    }

    @Test
    @DisplayName("Gateway 응답 파싱 실패는 pending 요청을 즉시 실패 처리한다")
    void listAgents_parseFailure_failsPendingRequest() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));
        transport.onSend = request -> transport.fail(OpenClawGatewayException.responseParseFailed(
                new IllegalArgumentException("invalid frame")
        ));

        // when & then
        assertThatThrownBy(client::listAgents)
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("gateway_response_parse_failed");

        client.close();
    }

    @Test
    @DisplayName("요청 처리 중 Gateway 연결이 끊기면 pending 요청을 즉시 실패 처리한다")
    void listAgents_disconnectDuringPending_failsPendingRequest() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));
        transport.onSend = request -> transport.disconnect();

        // when & then
        assertThatThrownBy(client::listAgents)
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("gateway_disconnected");

        client.close();
    }

    private OpenClawGatewayRpcClient newClient(FakeGatewayTransport transport) {
        AtomicInteger sequence = new AtomicInteger();
        return new OpenClawGatewayRpcClient(
                transport,
                new OpenClawPendingRequests(Executors.newSingleThreadScheduledExecutor()),
                () -> "req-" + sequence.incrementAndGet(),
                Duration.ofSeconds(1)
        );
    }

    private static class FakeGatewayTransport implements OpenClawGatewayTransport {

        private OpenClawGatewayConnectionContext connectedContext;
        private OpenClawRpcResponseHandler responseHandler;
        private Consumer<OpenClawGatewayException> failureHandler;
        private final List<OpenClawRpcRequest> sentRequests = new ArrayList<>();
        private Consumer<OpenClawRpcRequest> onSend = request -> {
        };

        @Override
        public void connect(
                OpenClawGatewayConnectionContext context,
                OpenClawRpcResponseHandler responseHandler,
                Consumer<OpenClawGatewayException> failureHandler
        ) {
            this.connectedContext = context;
            this.responseHandler = responseHandler;
            this.failureHandler = failureHandler;
        }

        @Override
        public void send(OpenClawRpcRequest request) {
            sentRequests.add(request);
            onSend.accept(request);
        }

        @Override
        public boolean isConnected() {
            return connectedContext != null;
        }

        @Override
        public void close() {
            connectedContext = null;
        }

        private void respond(OpenClawRpcResponse response) {
            responseHandler.handle(response);
        }

        private void disconnect() {
            connectedContext = null;
            failureHandler.accept(OpenClawGatewayException.gatewayDisconnected());
        }

        private void fail(OpenClawGatewayException exception) {
            failureHandler.accept(exception);
        }
    }
}
