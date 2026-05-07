package back.domain.gateway.exception;

import back.domain.gateway.client.rpc.dto.OpenClawRpcError;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawGatewayExceptionTest {

    @Test
    @DisplayName("Gateway 예외는 오류 코드와 요청 추적 정보를 보존한다")
    void constructor_gatewayError_success() {
        // given & when
        OpenClawGatewayException exception = new OpenClawGatewayException(
                OpenClawGatewayErrorCode.RPC_ERROR,
                "openclaw_rpc_timeout",
                "request req-1 timed out",
                "OpenClaw Gateway 요청 시간이 초과되었습니다.",
                "req-1",
                true,
                false);

        // then
        assertThat(exception.gatewayErrorCode()).isEqualTo("openclaw_rpc_timeout");
        assertThat(exception.requestId()).isEqualTo("req-1");
        assertThat(exception.retryable()).isTrue();
        assertThat(exception.pairingRequired()).isFalse();
        assertThat(exception.getErrorCode().status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getLogMessage()).isEqualTo("request req-1 timed out");
        assertThat(exception.getClientMessage()).isEqualTo("OpenClaw Gateway 요청 시간이 초과되었습니다.");
    }

    @Test
    @DisplayName("Gateway 예외 로그 메시지는 토큰 원문을 노출하지 않는다")
    void constructor_logMessageWithToken_masksToken() {
        // given & when
        OpenClawGatewayException exception = new OpenClawGatewayException(
                OpenClawGatewayErrorCode.UNAUTHORIZED,
                "openclaw_auth_failed",
                "connect failed token=secret-token Authorization: Bearer bearer-secret "
                        + "\"token\":\"json-secret\" token:colon-secret",
                "OpenClaw Gateway 인증에 실패했습니다.",
                "req-2",
                false,
                false);

        // then
        assertThat(exception.getLogMessage()).doesNotContain("secret-token");
        assertThat(exception.getLogMessage()).doesNotContain("bearer-secret");
        assertThat(exception.getLogMessage()).doesNotContain("json-secret");
        assertThat(exception.getLogMessage()).doesNotContain("colon-secret");
        assertThat(exception.getLogMessage()).contains("token=****");
        assertThat(exception.getLogMessage()).contains("\"token\":\"****\"");
        assertThat(exception.getLogMessage()).contains("token:****");
        assertThat(exception.getLogMessage()).contains("Authorization: Bearer ****");
    }

    @Test
    @DisplayName("RPC timeout 예외는 retryable=true와 requestId를 가진다")
    void rpcTimeout_validInput_success() {
        // when
        OpenClawGatewayException exception = OpenClawGatewayException.rpcTimeout(
                "agents.list",
                "req-timeout"
        );

        // then
        assertThat(exception.gatewayErrorCode()).isEqualTo("gateway_rpc_timeout");
        assertThat(exception.requestId()).isEqualTo("req-timeout");
        assertThat(exception.retryable()).isTrue();
        assertThat(exception.pairingRequired()).isFalse();
        assertThat(exception.getErrorCode().status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exception.getClientMessage()).isEqualTo("OpenClaw Gateway 요청 시간이 초과되었습니다.");
    }

    @Test
    @DisplayName("Gateway pairing 오류는 pairingRequired=true로 매핑한다")
    void fromRpcError_pairingRequired_mapsPairingFlag() {
        // given
        OpenClawRpcError error = new OpenClawRpcError(
                "PAIRING_REQUIRED",
                "pairing required",
                "req-pairing",
                Map.of("code", "PAIRING_REQUIRED")
        );

        // when
        OpenClawGatewayException exception = OpenClawGatewayException.fromRpcError(
                error,
                "fallback-req"
        );

        // then
        assertThat(exception.gatewayErrorCode()).isEqualTo("PAIRING_REQUIRED");
        assertThat(exception.requestId()).isEqualTo("req-pairing");
        assertThat(exception.retryable()).isFalse();
        assertThat(exception.pairingRequired()).isTrue();
        assertThat(exception.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getClientMessage()).isEqualTo("OpenClaw Gateway 연결 승인이 필요합니다.");
    }

    @Test
    @DisplayName("Gateway 인증 오류는 401 응답용 ErrorCode로 매핑한다")
    void fromRpcError_tokenInvalid_mapsUnauthorized() {
        // given
        OpenClawRpcError error = new OpenClawRpcError("TOKEN_INVALID", "invalid token");

        // when
        OpenClawGatewayException exception = OpenClawGatewayException.fromRpcError(
                error,
                "req-auth"
        );

        // then
        assertThat(exception.gatewayErrorCode()).isEqualTo("TOKEN_INVALID");
        assertThat(exception.requestId()).isEqualTo("req-auth");
        assertThat(exception.getErrorCode().status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getRsData().message()).isEqualTo("OpenClaw Gateway 인증에 실패했습니다.");
    }
}
