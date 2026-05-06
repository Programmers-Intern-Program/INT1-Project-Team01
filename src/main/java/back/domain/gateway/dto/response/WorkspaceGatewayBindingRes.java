package back.domain.gateway.dto.response;

import back.domain.gateway.entity.GatewayMode;
import back.domain.gateway.entity.WorkspaceGatewayBinding;

public record WorkspaceGatewayBindingRes(
        Long id, Long workspaceId, GatewayMode mode, String gatewayUrl, String maskedToken) {
    private static final int MIN_SECRET_LENGTH_FOR_MASKING = 8;
    private static final int VISIBLE_PREFIX_LENGTH = 4;
    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final String MASK_STRING = "****";

    public static WorkspaceGatewayBindingRes from(WorkspaceGatewayBinding binding) {
        return new WorkspaceGatewayBindingRes(
                binding.getId(),
                binding.getWorkspace().getId(),
                binding.getMode(),
                binding.getGatewayUrl(),
                mask(binding.getToken()));
    }

    private static String mask(String secret) {
        if (secret == null || secret.length() <= MIN_SECRET_LENGTH_FOR_MASKING) {
            return MASK_STRING;
        }
        return secret.substring(0, VISIBLE_PREFIX_LENGTH)
                + MASK_STRING
                + secret.substring(secret.length() - VISIBLE_SUFFIX_LENGTH);
    }
}
