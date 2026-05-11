package back.domain.gateway.dto.response;

import back.domain.gateway.entity.GatewayConnectionStatus;

public record WorkspaceGatewayConnectionTestRes(
        GatewayConnectionStatus status,
        boolean connected,
        String gatewayUrl,
        String message,
        int agentCount) {

    public static WorkspaceGatewayConnectionTestRes connected(String gatewayUrl, int agentCount) {
        return new WorkspaceGatewayConnectionTestRes(
                GatewayConnectionStatus.CONNECTED,
                true,
                gatewayUrl,
                "OpenClaw Gateway 연결에 성공했습니다.",
                agentCount);
    }

    public static WorkspaceGatewayConnectionTestRes failed(
            GatewayConnectionStatus status, String gatewayUrl, String message) {
        return new WorkspaceGatewayConnectionTestRes(status, false, gatewayUrl, message, 0);
    }
}
