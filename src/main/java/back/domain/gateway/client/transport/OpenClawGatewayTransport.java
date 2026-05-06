package back.domain.gateway.client.transport;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.client.rpc.OpenClawRpcResponseHandler;
import back.domain.gateway.client.rpc.dto.OpenClawRpcRequest;
import back.domain.gateway.exception.OpenClawGatewayException;

import java.util.function.Consumer;

public interface OpenClawGatewayTransport {

    void connect(
            OpenClawGatewayConnectionContext context,
            OpenClawRpcResponseHandler responseHandler,
            Consumer<OpenClawGatewayException> failureHandler
    );

    void send(OpenClawRpcRequest request);

    boolean isConnected();

    void close();
}
