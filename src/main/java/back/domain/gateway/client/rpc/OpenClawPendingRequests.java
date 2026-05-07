package back.domain.gateway.client.rpc;

import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.exception.OpenClawGatewayException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OpenClawPendingRequests implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, PendingRpcRequest> pendingRequests =
            new ConcurrentHashMap<>();

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Pending request registry owns scheduling through the provided executor."
    )
    public OpenClawPendingRequests(ScheduledExecutorService scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public CompletableFuture<Map<String, Object>> register(
            String requestId,
            String method,
            Duration timeout
    ) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        PendingRpcRequest request = new PendingRpcRequest(requestId, method, future);
        pendingRequests.put(requestId, request);
        var timeoutTask = scheduler.schedule(
                () -> timeout(requestId),
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
        );
        request.setTimeoutTask(timeoutTask);
        return future;
    }

    public void complete(OpenClawRpcResponse response) {
        PendingRpcRequest request = pendingRequests.remove(response.id());
        if (request == null) {
            return;
        }

        request.cancelTimeout();
        if (response.ok()) {
            request.complete(response.payload() == null ? Map.of() : response.payload());
            return;
        }

        request.fail(OpenClawGatewayException.fromRpcError(
                response.error(),
                response.id()
        ));
    }

    public void cancel(String requestId) {
        PendingRpcRequest request = pendingRequests.remove(requestId);
        if (request != null) {
            request.cancelTimeout();
        }
    }

    public void failAll(OpenClawGatewayException exception) {
        pendingRequests.forEach((requestId, request) -> {
            if (pendingRequests.remove(requestId, request)) {
                request.cancelTimeout();
                request.fail(exception);
            }
        });
    }

    public int pendingCount() {
        return pendingRequests.size();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private void timeout(String requestId) {
        PendingRpcRequest request = pendingRequests.remove(requestId);
        if (request == null) {
            return;
        }
        request.fail(OpenClawGatewayException.rpcTimeout(
                request.method(),
                request.requestId()
        ));
    }
}
