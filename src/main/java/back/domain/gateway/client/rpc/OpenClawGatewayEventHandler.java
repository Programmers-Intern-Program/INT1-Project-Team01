package back.domain.gateway.client.rpc;

import back.domain.gateway.client.rpc.dto.OpenClawGatewayEvent;

@FunctionalInterface
public interface OpenClawGatewayEventHandler {

    void handle(OpenClawGatewayEvent event);
}
