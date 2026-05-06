package back.domain.gateway.client.support;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.client.rpc.OpenClawRpcResponseHandler;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;
import back.domain.gateway.client.transport.OpenClawGatewayTransport;
import back.domain.gateway.exception.OpenClawGatewayException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MockOpenClawGatewayTransport implements OpenClawGatewayTransport {

    private OpenClawGatewayConnectionContext connectedContext;
    private OpenClawRpcResponseHandler responseHandler;
    private Consumer<OpenClawGatewayException> failureHandler;
    private final List<OpenClawRpcRequest> sentRequests = new ArrayList<>();
    private Consumer<OpenClawRpcRequest> onSend = request -> {};

    @Override
    public void connect(
            OpenClawGatewayConnectionContext context,
            OpenClawRpcResponseHandler responseHandler,
            Consumer<OpenClawGatewayException> failureHandler) {
        this.connectedContext = context;
        this.responseHandler = responseHandler;
        this.failureHandler = failureHandler;
    }

    @Override
    public void send(OpenClawRpcRequest request) {
        sentRequests.add(request);
        onSend.accept(request);
    }

    @Override
    public boolean isConnected() {
        return connectedContext != null;
    }

    @Override
    public void close() {
        connectedContext = null;
    }

    public OpenClawGatewayConnectionContext connectedContext() {
        return connectedContext;
    }

    public List<OpenClawRpcRequest> sentRequests() {
        return List.copyOf(sentRequests);
    }

    public void onSend(Consumer<OpenClawRpcRequest> onSend) {
        this.onSend = onSend == null ? request -> {} : onSend;
    }

    public void respond(OpenClawRpcResponse response) {
        responseHandler.handle(response);
    }

    public void disconnect() {
        connectedContext = null;
        failureHandler.accept(OpenClawGatewayException.gatewayDisconnected());
    }

    public void fail(OpenClawGatewayException exception) {
        failureHandler.accept(exception);
    }
}
