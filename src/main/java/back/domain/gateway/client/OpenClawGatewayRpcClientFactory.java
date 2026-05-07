package back.domain.gateway.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenClawGatewayRpcClientFactory implements OpenClawGatewayClientFactory {

    private final Duration rpcTimeout;

    public OpenClawGatewayRpcClientFactory(@Value("${openclaw.gateway.rpc-timeout:10s}") Duration rpcTimeout) {
        this.rpcTimeout = rpcTimeout;
    }

    @Override
    public OpenClawGatewayClient create() {
        return OpenClawGatewayRpcClient.webSocket(rpcTimeout);
    }
}
