package back.domain.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenClawGatewayClientTest {

    @Test
    @DisplayName("Gateway client는 Agent 생성/파일 동기화/채팅 실행 계약을 제공한다")
    void clientContract_requiredMethods_success() throws NoSuchMethodException {
        // when
        Method connect = OpenClawGatewayClient.class.getMethod("connect", OpenClawGatewayConnectionContext.class);
        Method listAgents = OpenClawGatewayClient.class.getMethod("listAgents");
        Method createAgent = OpenClawGatewayClient.class.getMethod("createAgent", OpenClawAgentCreateCommand.class);
        Method setAgentFile = OpenClawGatewayClient.class.getMethod("setAgentFile", OpenClawAgentFileCommand.class);
        Method sendChat = OpenClawGatewayClient.class.getMethod("sendChat", OpenClawChatCommand.class);

        // then
        assertThat(connect.getReturnType()).isEqualTo(Void.TYPE);
        assertThat(listAgents.getReturnType()).isEqualTo(List.class);
        assertThat(createAgent.getReturnType()).isEqualTo(OpenClawAgentSummary.class);
        assertThat(setAgentFile.getReturnType()).isEqualTo(Void.TYPE);
        assertThat(sendChat.getReturnType()).isEqualTo(OpenClawChatResult.class);
    }
}
