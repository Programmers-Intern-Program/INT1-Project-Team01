package back.domain.gateway.dto.response;

import java.time.LocalDateTime;

import back.domain.gateway.entity.GatewayConnectionStatus;
import back.domain.gateway.entity.GatewayMode;
import back.domain.gateway.entity.WorkspaceGatewayBinding;

public record WorkspaceGatewayStatusRes(
        WorkspaceGatewayBindingStatus status,
        boolean bound,
        Long bindingId,
        GatewayMode mode,
        String gatewayUrl,
        String maskedToken,
        GatewayConnectionStatus lastStatus,
        LocalDateTime lastCheckedAt,
        String lastError,
        LocalDateTime updatedAt) {

    public static WorkspaceGatewayStatusRes unbound() {
        return new WorkspaceGatewayStatusRes(
                WorkspaceGatewayBindingStatus.UNBOUND,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static WorkspaceGatewayStatusRes from(WorkspaceGatewayBinding binding) {
        return new WorkspaceGatewayStatusRes(
                WorkspaceGatewayBindingStatus.BOUND,
                true,
                binding.getId(),
                binding.getMode(),
                binding.getGatewayUrl(),
                WorkspaceGatewayBindingRes.maskToken(binding.getToken()),
                binding.getLastStatus(),
                binding.getLastCheckedAt(),
                binding.getLastError(),
                binding.getUpdatedAt());
    }
}
