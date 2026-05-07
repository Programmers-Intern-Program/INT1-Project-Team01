package back.domain.gateway.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenClawGatewaySmokeTest {

    @Test
    @DisplayName("실제 OpenClaw Gateway가 명시적으로 설정되면 agents.list smoke test를 실행한다")
    void actualGateway_agentsList_smoke() {
        // given
        String enabled = System.getenv("OPENCLAW_GATEWAY_SMOKE");
        String gatewayUrl = System.getenv("OPENCLAW_GATEWAY_URL");
        String token = System.getenv("OPENCLAW_GATEWAY_TOKEN");
        assumeTrue("true".equalsIgnoreCase(enabled));
        assumeTrue(gatewayUrl != null && !gatewayUrl.isBlank());
        assumeTrue(token != null && !token.isBlank());

        OpenClawGatewayRpcClient client = OpenClawGatewayRpcClient.webSocket(Duration.ofSeconds(30));
        try {
            client.connect(new OpenClawGatewayConnectionContext(gatewayUrl, token));

            // when & then
            assertThat(client.listAgents()).isNotNull();
        } finally {
            client.close();
        }
    }
}
