package back.domain.gateway.client.rpc;

import back.domain.gateway.client.rpc.dto.OpenClawRpcResponse;

@FunctionalInterface
public interface OpenClawRpcResponseHandler {

    void handle(OpenClawRpcResponse response);
}
