package back.domain.gateway.client.rpc.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawRpcResponseTest {

    @Test
    @DisplayName("성공 RPC 응답은 ok=true와 payload를 가진다")
    void success_validInput_success() {
        // given
        Map<String, Object> payload = Map.of("count", 1);

        // when
        OpenClawRpcResponse response = OpenClawRpcResponse.success("req-1", payload);

        // then
        assertThat(response.type()).isEqualTo("res");
        assertThat(response.id()).isEqualTo("req-1");
        assertThat(response.ok()).isTrue();
        assertThat(response.payload()).isEqualTo(payload);
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("실패 RPC 응답은 ok=false와 error를 가진다")
    void failure_validInput_success() {
        // given
        OpenClawRpcError error = new OpenClawRpcError("AGENT_NOT_FOUND", "Agent not found");

        // when
        OpenClawRpcResponse response = OpenClawRpcResponse.failure("req-1", error);

        // then
        assertThat(response.type()).isEqualTo("res");
        assertThat(response.id()).isEqualTo("req-1");
        assertThat(response.ok()).isFalse();
        assertThat(response.payload()).isNull();
        assertThat(response.error()).isEqualTo(error);
    }
}
