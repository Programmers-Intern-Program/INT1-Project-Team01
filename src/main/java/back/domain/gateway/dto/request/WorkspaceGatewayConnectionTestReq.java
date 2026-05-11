package back.domain.gateway.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WorkspaceGatewayConnectionTestReq(@NotBlank String gatewayUrl, @NotBlank String token) {}
