package back.domain.gateway.dto.response;

public record WorkspaceGatewayConnectionTestRes(
        GatewayConnectionTestStatus status,
        boolean connected,
        String gatewayUrl,
        String message,
        int agentCount) {

    public static WorkspaceGatewayConnectionTestRes connected(String gatewayUrl, int agentCount) {
        return new WorkspaceGatewayConnectionTestRes(
                GatewayConnectionTestStatus.CONNECTED,
                true,
                gatewayUrl,
                "OpenClaw Gateway 연결에 성공했습니다.",
                agentCount);
    }

    public static WorkspaceGatewayConnectionTestRes failed(
            GatewayConnectionTestStatus status, String gatewayUrl, String message) {
        return new WorkspaceGatewayConnectionTestRes(status, false, gatewayUrl, message, 0);
    }
}
