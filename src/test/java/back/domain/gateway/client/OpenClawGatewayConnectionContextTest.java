package back.domain.gateway.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawGatewayConnectionContextTest {

    @Test
    @DisplayName("Gateway connection context 문자열 표현은 token 원문을 노출하지 않는다")
    void toString_masksToken() {
        // given
        OpenClawGatewayConnectionContext context = new OpenClawGatewayConnectionContext(
                "ws://localhost:3999",
                "secret-token"
        );

        // when
        String value = context.toString();

        // then
        assertThat(value).contains("ws://localhost:3999");
        assertThat(value).contains("token=****");
        assertThat(value).doesNotContain("secret-token");
    }
}
