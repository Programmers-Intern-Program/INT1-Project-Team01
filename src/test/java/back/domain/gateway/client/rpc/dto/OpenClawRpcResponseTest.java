package back.domain.gateway.client.rpc.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("성공 RPC 응답 payload는 null 필드 값을 보존한다")
    void deserialize_payloadWithNullValue_preservesNullValue() throws Exception {
        // given
        String json = """
                {"type":"res","id":"req-1","ok":true,"payload":{"nextCursor":null}}
                """;

        // when
        OpenClawRpcResponse response = objectMapper().readValue(json, OpenClawRpcResponse.class);

        // then
        assertThat(response.payload()).containsEntry("nextCursor", null);
        assertThatThrownBy(() -> response.payload().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("실패 RPC 응답 details는 null 필드 값을 보존한다")
    void deserialize_errorDetailsWithNullValue_preservesGatewayError() throws Exception {
        // given
        String json = """
                {
                  "type":"res",
                  "id":"req-1",
                  "ok":false,
                  "error":{
                    "code":"PAIRING_REQUIRED",
                    "message":"pairing required",
                    "details":{"approvedAt":null}
                  }
                }
                """;

        // when
        OpenClawRpcResponse response = objectMapper().readValue(json, OpenClawRpcResponse.class);

        // then
        assertThat(response.error().code()).isEqualTo("PAIRING_REQUIRED");
        assertThat(response.error().details()).containsEntry("approvedAt", null);
        assertThatThrownBy(() -> response.error().details().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
