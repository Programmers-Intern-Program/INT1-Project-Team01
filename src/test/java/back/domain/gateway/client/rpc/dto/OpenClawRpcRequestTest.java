package back.domain.gateway.client.rpc.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenClawRpcRequestTest {

    @Test
    @DisplayName("RPC 요청 frame은 req 타입, id, method, params를 가진다")
    void of_validInput_success() {
        // given
        Map<String, Object> params = Map.of("agentId", "backend-agent-1");

        // when
        OpenClawRpcRequest request = OpenClawRpcRequest.of("req-1", "agents.list", params);

        // then
        assertThat(request.type()).isEqualTo("req");
        assertThat(request.id()).isEqualTo("req-1");
        assertThat(request.method()).isEqualTo("agents.list");
        assertThat(request.params()).isEqualTo(params);
    }

    @Test
    @DisplayName("RPC 요청 params가 null이면 빈 객체로 정규화한다")
    void of_nullParams_usesEmptyParams() {
        // when
        OpenClawRpcRequest request = OpenClawRpcRequest.of("req-1", "agents.list", null);

        // then
        assertThat(request.params()).isEmpty();
    }

    @Test
    @DisplayName("RPC 요청 params는 null 필드 값을 보존한다")
    void of_paramsWithNullValue_preservesNullValue() {
        // given
        Map<String, Object> params = new HashMap<>();
        params.put("optionalAgentId", null);

        // when
        OpenClawRpcRequest request = OpenClawRpcRequest.of("req-1", "agents.list", params);

        // then
        assertThat(request.params()).containsEntry("optionalAgentId", null);
        assertThatThrownBy(() -> request.params().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("RPC 요청 id와 method는 비어 있을 수 없다")
    void of_blankRequiredField_throwsException() {
        // when & then
        assertThatThrownBy(() -> OpenClawRpcRequest.of(" ", "agents.list", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");

        assertThatThrownBy(() -> OpenClawRpcRequest.of("req-1", " ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
    }
}
