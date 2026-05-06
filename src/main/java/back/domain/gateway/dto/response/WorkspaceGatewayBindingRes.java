package back.domain.gateway.dto.response;

import back.domain.gateway.entity.GatewayMode;
import back.domain.gateway.entity.WorkspaceGatewayBinding;

public record WorkspaceGatewayBindingRes(
        Long id, Long workspaceId, GatewayMode mode, String gatewayUrl, String maskedToken) {
    private static final int VISIBLE_PREFIX_LENGTH = 4;
    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final int MIN_HIDDEN_LENGTH = 4;
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
        if (secret == null || secret.length() <= VISIBLE_PREFIX_LENGTH + VISIBLE_SUFFIX_LENGTH + MIN_HIDDEN_LENGTH) {
            return MASK_STRING;
        }
        return secret.substring(0, VISIBLE_PREFIX_LENGTH)
                + MASK_STRING
                + secret.substring(secret.length() - VISIBLE_SUFFIX_LENGTH);
    }
}
