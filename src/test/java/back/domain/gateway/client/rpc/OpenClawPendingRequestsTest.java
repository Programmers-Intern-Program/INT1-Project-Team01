package back.domain.gateway.client.rpc;

import back.domain.gateway.client.rpc.dto.OpenClawRpcError;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.exception.OpenClawGatewayException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenClawPendingRequestsTest {

    @Test
    @DisplayName("응답 id와 일치하는 pending request만 완료한다")
    void complete_matchingResponse_success() throws Exception {
        // given
        OpenClawPendingRequests pendingRequests = newPendingRequests();
        var first = pendingRequests.register("req-1", "agents.list", Duration.ofSeconds(10));
        var second = pendingRequests.register("req-2", "agents.list", Duration.ofSeconds(10));

        // when
        pendingRequests.complete(OpenClawRpcResponse.success("req-2", Map.of("ok", true)));

        // then
        assertThat(second.get(100, TimeUnit.MILLISECONDS)).isEqualTo(Map.of("ok", true));
        assertThat(first).isNotDone();
        assertThat(pendingRequests.pendingCount()).isEqualTo(1);

        pendingRequests.close();
    }

    @Test
    @DisplayName("timeout이 발생하면 pending request를 제거하고 Gateway timeout 예외로 완료한다")
    void register_timeout_failAndRemovePending() {
        // given
        OpenClawPendingRequests pendingRequests = newPendingRequests();
        var future = pendingRequests.register(
                "req-timeout",
                "agents.list",
                Duration.ofMillis(10)
        );

        // when & then
        assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("gateway_rpc_timeout");
        assertThat(pendingRequests.pendingCount()).isZero();

        pendingRequests.close();
    }

    @Test
    @DisplayName("실패 RPC 응답은 Gateway 예외로 매핑한다")
    void complete_errorResponse_mapsGatewayException() {
        // given
        OpenClawPendingRequests pendingRequests = newPendingRequests();
        var future = pendingRequests.register("req-error", "agents.list", Duration.ofSeconds(1));

        // when
        pendingRequests.complete(OpenClawRpcResponse.failure(
                "req-error",
                new OpenClawRpcError("TOKEN_INVALID", "invalid token")
        ));

        // then
        assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(OpenClawGatewayException.class)
                .extracting("gatewayErrorCode")
                .isEqualTo("TOKEN_INVALID");
        assertThat(pendingRequests.pendingCount()).isZero();

        pendingRequests.close();
    }

    private OpenClawPendingRequests newPendingRequests() {
        return new OpenClawPendingRequests(Executors.newSingleThreadScheduledExecutor());
    }
}
