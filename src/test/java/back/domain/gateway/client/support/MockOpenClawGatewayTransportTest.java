package back.domain.gateway.client.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.exception.OpenClawGatewayException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockOpenClawGatewayTransportTest {

    @Test
    @DisplayName("connect 전 respond를 호출하면 명확한 예외를 던진다")
    void respond_beforeConnect_throwsException() {
        // given
        MockOpenClawGatewayTransport transport = new MockOpenClawGatewayTransport();
        OpenClawRpcResponse response = OpenClawRpcResponse.success("req-1", Map.of());

        // when & then
        assertThatThrownBy(() -> transport.respond(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    @DisplayName("connect 전 fail을 호출하면 명확한 예외를 던진다")
    void fail_beforeConnect_throwsException() {
        // given
        MockOpenClawGatewayTransport transport = new MockOpenClawGatewayTransport();

        // when & then
        assertThatThrownBy(() -> transport.fail(OpenClawGatewayException.gatewayDisconnected()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    @DisplayName("connect 전 disconnect를 호출하면 명확한 예외를 던진다")
    void disconnect_beforeConnect_throwsException() {
        // given
        MockOpenClawGatewayTransport transport = new MockOpenClawGatewayTransport();

        // when & then
        assertThatThrownBy(transport::disconnect)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }
}
