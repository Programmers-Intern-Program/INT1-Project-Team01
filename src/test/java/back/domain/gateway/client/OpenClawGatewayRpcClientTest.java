package back.domain.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import back.domain.gateway.client.rpc.OpenClawPendingRequests;
import back.domain.gateway.client.rpc.OpenClawGatewayEventHandler;
import back.domain.gateway.client.rpc.OpenClawRpcResponseHandler;
import back.domain.gateway.client.rpc.dto.OpenClawGatewayEvent;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.client.transport.OpenClawGatewayTransport;
import back.domain.gateway.exception.OpenClawGatewayException;

class OpenClawGatewayRpcClientTest {

    private static final String CONNECT_METHOD = "connect";

    @Test
    @DisplayName("connect는 transport 연결 후 Gateway connect handshake를 먼저 보낸다")
    void connect_validContext_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        OpenClawGatewayRpcClient client = newClient(transport);
        OpenClawGatewayConnectionContext context =
                new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token");

        // when
        client.connect(context);

        // then
        assertThat(transport.connectedContext).isEqualTo(context);
        assertThat(transport.responseHandler).isNotNull();
        assertThat(transport.eventHandler).isNotNull();
        assertThat(transport.isConnected()).isTrue();
        assertThat(transport.sentRequests).hasSize(1);
        OpenClawRpcRequest connectRequest = transport.sentRequests.getFirst();
        assertThat(connectRequest.method()).isEqualTo(CONNECT_METHOD);
        assertThat(connectRequest.params()).containsEntry("minProtocol", 3);
        assertThat(connectRequest.params()).containsEntry("maxProtocol", 3);
        assertThat(connectRequest.params()).containsKey("auth");
        assertThat(connectRequest.params()).containsEntry("role", "operator");
        assertThat(connectRequest.params()).containsEntry(
                "client",
                Map.of(
                        "id", "gateway-client",
                        "version", "1.0.0",
                        "platform", "java",
                        "mode", "backend"));

        client.close();
    }

    @Test
    @DisplayName("connect는 null context를 허용하지 않는다")
    void connect_nullContext_throwsException() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        OpenClawGatewayRpcClient client = newClient(transport);

        // when & then
        assertThatThrownBy(() -> client.connect(null)).isInstanceOf(NullPointerException.class);
        assertThat(transport.isConnected()).isFalse();
        assertThat(transport.sentRequests).isEmpty();

        client.close();
    }

    @Test
    @DisplayName("connect handshake 실패 시 Gateway RPC 예외로 처리하고 연결을 닫는다")
    void connect_handshakeFailure_throwsException() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.autoRespondToConnect = false;
        transport.onSend = request -> transport.respond(OpenClawRpcResponse.failure(
                request.id(),
                new back.domain.gateway.client.rpc.dto.OpenClawRpcError(
                        "UNAUTHORIZED",
                        "invalid gateway token")));
        OpenClawGatewayRpcClient client = newClient(transport);

        // when & then
        assertThatThrownBy(() -> client.connect(
                        new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token")))
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("UNAUTHORIZED");
        assertThat(transport.isConnected()).isFalse();

        client.close();
    }

    @Test
    @DisplayName("agents.list는 RPC 요청을 보내고 응답 payload를 Agent summary로 변환한다")
    void listAgents_successResponse_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> transport.respond(OpenClawRpcResponse.success(
                request.id(),
                Map.of(
                        "agents",
                        List.of(
                                Map.of("id", "agent-1", "name", "Backend Agent"),
                                Map.of("agentId", "agent-2", "name", "Reviewer Agent")))));
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        List<OpenClawAgentSummary> agents = client.listAgents();

        // then
        List<OpenClawRpcRequest> businessRequests = businessRequests(transport);
        assertThat(businessRequests).hasSize(1);
        assertThat(businessRequests.getFirst().method()).isEqualTo("agents.list");
        assertThat(agents)
                .containsExactly(
                        new OpenClawAgentSummary("agent-1", "Backend Agent"),
                        new OpenClawAgentSummary("agent-2", "Reviewer Agent"));

        client.close();
    }

    @Test
    @DisplayName("agents.list는 식별자나 이름이 없는 agent item을 건너뛴다")
    void listAgents_malformedAgentItems_skipsInvalidItems() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> transport.respond(OpenClawRpcResponse.success(
                request.id(),
                Map.of(
                        "agents",
                        List.of(
                                Map.of("id", "agent-1", "name", "Backend Agent"),
                                Map.of("id", "agent-without-name"),
                                Map.of("name", "Agent Without Id"),
                                Map.of("id", " ", "name", "Blank Id Agent"),
                                "not-an-agent"))));
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        List<OpenClawAgentSummary> agents = client.listAgents();

        // then
        assertThat(agents).containsExactly(new OpenClawAgentSummary("agent-1", "Backend Agent"));

        client.close();
    }

    @Test
    @DisplayName("agents.create는 optional parameter를 제외하고 RPC 요청을 보낸 뒤 agent summary를 반환한다")
    void createAgent_successResponse_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> transport.respond(OpenClawRpcResponse.success(
                request.id(), Map.of("agent", Map.of("agentId", "openclaw-agent-1", "name", "Backend Agent"))));
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        OpenClawAgentSummary summary =
                client.createAgent(new OpenClawAgentCreateCommand("Backend Agent", "~/.openclaw/workspace-1", null));

        // then
        List<OpenClawRpcRequest> businessRequests = businessRequests(transport);
        assertThat(businessRequests).hasSize(1);
        OpenClawRpcRequest request = businessRequests.getFirst();
        assertThat(request.method()).isEqualTo("agents.create");
        assertThat(request.params()).containsEntry("name", "Backend Agent");
        assertThat(request.params()).containsEntry("workspace", "~/.openclaw/workspace-1");
        assertThat(request.params()).doesNotContainKey("emoji");
        assertThat(summary).isEqualTo(new OpenClawAgentSummary("openclaw-agent-1", "Backend Agent"));

        client.close();
    }

    @Test
    @DisplayName("agents.create 응답에 agent id가 없으면 Gateway 응답 파싱 실패로 처리한다")
    void createAgent_missingAgentId_throwsException() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request ->
                transport.respond(OpenClawRpcResponse.success(request.id(), Map.of("name", "Backend Agent")));
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when & then
        assertThatThrownBy(() -> client.createAgent(new OpenClawAgentCreateCommand("Backend Agent", null, null)))
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("gateway_response_parse_failed");

        client.close();
    }

    @Test
    @DisplayName("agents.files.set은 agentId, name, content를 RPC 요청으로 보낸다")
    void setAgentFile_successResponse_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> transport.respond(OpenClawRpcResponse.success(request.id(), Map.of()));
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        client.setAgentFile(new OpenClawAgentFileCommand("openclaw-agent-1", "AGENTS.md", "You are a backend agent."));

        // then
        List<OpenClawRpcRequest> businessRequests = businessRequests(transport);
        assertThat(businessRequests).hasSize(1);
        OpenClawRpcRequest request = businessRequests.getFirst();
        assertThat(request.method()).isEqualTo("agents.files.set");
        assertThat(request.params()).containsEntry("agentId", "openclaw-agent-1");
        assertThat(request.params()).containsEntry("name", "AGENTS.md");
        assertThat(request.params()).containsEntry("content", "You are a backend agent.");

        client.close();
    }

    @Test
    @DisplayName("chat.send는 sessionKey, message, idempotencyKey를 RPC 요청으로 보내고 "
            + "final text를 반환한다")
    void sendChat_successResponse_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> transport.respond(OpenClawRpcResponse.success(
                request.id(),
                Map.of(
                        "chat",
                        Map.of(
                                "sessionKey",
                                "agent:openclaw-agent-1:workspace-1-execution-10",
                                "finalText",
                                "작업을 완료했습니다."))));
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        OpenClawChatResult result = client.sendChat(new OpenClawChatCommand(
                "openclaw-agent-1", "workspace-1-execution-10", "회원가입 API를 리뷰해줘", "idem-1"));

        // then
        List<OpenClawRpcRequest> businessRequests = businessRequests(transport);
        assertThat(businessRequests).hasSize(1);
        OpenClawRpcRequest request = businessRequests.getFirst();
        assertThat(request.method()).isEqualTo("chat.send");
        assertThat(request.params())
                .containsEntry("sessionKey", "agent:openclaw-agent-1:workspace-1-execution-10")
                .containsEntry("message", "회원가입 API를 리뷰해줘")
                .containsEntry("idempotencyKey", "idem-1");
        assertThat(result)
                .isEqualTo(new OpenClawChatResult(
                        "agent:openclaw-agent-1:workspace-1-execution-10", "작업을 완료했습니다."));

        client.close();
    }

    @Test
    @DisplayName("chat.send는 agent/chat 이벤트 스트림을 누적해 final text를 반환한다")
    void sendChat_eventStream_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> {
            String sessionKey = (String) request.params().get("sessionKey");
            transport.emit(OpenClawGatewayEvent.of(
                    "agent",
                    Map.of(
                            "sessionKey", sessionKey,
                            "stream", "assistant",
                            "data", Map.of("delta", "작업을 "))));
            transport.emit(OpenClawGatewayEvent.of(
                    "agent",
                    Map.of(
                            "sessionKey", sessionKey,
                            "stream", "assistant",
                            "data", Map.of("delta", "완료했습니다."))));
            transport.emit(OpenClawGatewayEvent.of(
                    "chat",
                    Map.of(
                            "sessionKey", sessionKey,
                            "state", "final")));
        };
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        OpenClawChatResult result = client.sendChat(new OpenClawChatCommand(
                "openclaw-agent-1", "workspace-1-execution-10", "회원가입 API를 리뷰해줘", "idem-1"));

        // then
        List<OpenClawRpcRequest> businessRequests = businessRequests(transport);
        assertThat(businessRequests).hasSize(1);
        assertThat(businessRequests.getFirst().method()).isEqualTo("chat.send");
        assertThat(result)
                .isEqualTo(new OpenClawChatResult(
                        "agent:openclaw-agent-1:workspace-1-execution-10", "작업을 완료했습니다."));

        client.close();
    }

    @Test
    @DisplayName("chat.send RPC ack 이후 도착한 이벤트 스트림으로 final text를 반환한다")
    void sendChat_ackThenEventStream_success() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        transport.onSend = request -> {
            String sessionKey = (String) request.params().get("sessionKey");
            transport.respond(OpenClawRpcResponse.success(
                    request.id(),
                    Map.of("chat", Map.of("sessionKey", sessionKey))));
            transport.emit(OpenClawGatewayEvent.of(
                    "agent",
                    Map.of(
                            "sessionKey", sessionKey,
                            "stream", "assistant",
                            "data", Map.of("delta", "ack 이후 완료"))));
            transport.emit(OpenClawGatewayEvent.of(
                    "chat",
                    Map.of(
                            "sessionKey", sessionKey,
                            "state", "final")));
        };
        OpenClawGatewayRpcClient client = newClient(transport);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        OpenClawChatResult result = client.sendChat(new OpenClawChatCommand(
                "openclaw-agent-1", "workspace-1-execution-10", "회원가입 API를 리뷰해줘", "idem-1"));

        // then
        assertThat(result)
                .isEqualTo(new OpenClawChatResult(
                        "agent:openclaw-agent-1:workspace-1-execution-10", "ack 이후 완료"));

        client.close();
    }

    @Test
    @DisplayName("chat.send는 동일 sessionKey pending 중복 등록을 거부한다")
    void sendChat_duplicatePendingSession_rejectsSecondRequest() {
        // given
        FakeGatewayTransport transport = new FakeGatewayTransport();
        AtomicReference<OpenClawGatewayRpcClient> clientRef = new AtomicReference<>();
        OpenClawChatCommand command = new OpenClawChatCommand(
                "openclaw-agent-1", "workspace-1-execution-10", "회원가입 API를 리뷰해줘", "idem-1");
        transport.onSend = request -> {
            assertThatThrownBy(() -> clientRef.get().sendChat(command))
                    .isInstanceOf(OpenClawGatewayException.class)
                    .extracting("gatewayErrorCode")
                    .isEqualTo("gateway_send_failed");

            String sessionKey = (String) request.params().get("sessionKey");
            transport.emit(OpenClawGatewayEvent.of(
                    "agent",
                    Map.of(
                            "sessionKey", sessionKey,
                            "stream", "assistant",
                            "data", Map.of("delta", "첫 요청 완료"))));
            transport.emit(OpenClawGatewayEvent.of(
                    "chat",
                    Map.of(
                            "sessionKey", sessionKey,
                            "state", "final")));
        };
        OpenClawGatewayRpcClient client = newClient(transport);
        clientRef.set(client);
        client.connect(new OpenClawGatewayConnectionContext("ws://localhost:3999", "secret-token"));

        // when
        OpenClawChatResult result = client.sendChat(command);

        // then
        assertThat(businessRequests(transport)).hasSize(1);
        assertThat(result.finalText()).isEqualTo("첫 요청 완료");

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
        transport.onSend = request -> transport.fail(
                OpenClawGatewayException.responseParseFailed(new IllegalArgumentException("invalid frame")));

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
                Duration.ofSeconds(1));
    }

    private List<OpenClawRpcRequest> businessRequests(FakeGatewayTransport transport) {
        return transport.sentRequests.stream()
                .filter(request -> !CONNECT_METHOD.equals(request.method()))
                .toList();
    }

    private static class FakeGatewayTransport implements OpenClawGatewayTransport {

        private OpenClawGatewayConnectionContext connectedContext;
        private OpenClawRpcResponseHandler responseHandler;
        private OpenClawGatewayEventHandler eventHandler;
        private Consumer<OpenClawGatewayException> failureHandler;
        private final List<OpenClawRpcRequest> sentRequests = new ArrayList<>();
        private boolean autoRespondToConnect = true;
        private Consumer<OpenClawRpcRequest> onSend = request -> {};

        @Override
        public void connect(
                OpenClawGatewayConnectionContext context,
                OpenClawRpcResponseHandler responseHandler,
                OpenClawGatewayEventHandler eventHandler,
                Consumer<OpenClawGatewayException> failureHandler) {
            this.connectedContext = context;
            this.responseHandler = responseHandler;
            this.eventHandler = eventHandler;
            this.failureHandler = failureHandler;
        }

        @Override
        public void send(OpenClawRpcRequest request) {
            sentRequests.add(request);
            if (autoRespondToConnect && CONNECT_METHOD.equals(request.method())) {
                respond(OpenClawRpcResponse.success(request.id(), Map.of()));
                return;
            }
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

        private void emit(OpenClawGatewayEvent event) {
            eventHandler.handle(event);
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
