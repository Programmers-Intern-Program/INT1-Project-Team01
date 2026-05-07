package back.domain.gateway.client.rpc;

import back.domain.gateway.exception.OpenClawGatewayException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

class PendingRpcRequest {

    private final String requestId;
    private final String method;
    private final CompletableFuture<Map<String, Object>> future;
    private ScheduledFuture<?> timeoutTask;

    PendingRpcRequest(
            String requestId,
            String method,
            CompletableFuture<Map<String, Object>> future
    ) {
        this.requestId = requestId;
        this.method = method;
        this.future = future;
    }

    String requestId() {
        return requestId;
    }

    String method() {
        return method;
    }

    void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    void complete(Map<String, Object> payload) {
        future.complete(payload);
    }

    void fail(OpenClawGatewayException exception) {
        future.completeExceptionally(exception);
    }

    void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
    }
}
