package back.domain.execution.dto.request;

public record TaskExecutionRunCommand(
        Long workspaceId,
        Long taskId,
        Long assignedAgentId,
        Long repositoryId,
        String prompt,
        boolean createPr,
        String openClawSessionKeyOverride) {

    private static final int OPEN_CLAW_SESSION_KEY_MAX_LENGTH = 220;

    public TaskExecutionRunCommand(
            Long workspaceId,
            Long taskId,
            Long assignedAgentId,
            Long repositoryId,
            String prompt,
            boolean createPr) {
        this(workspaceId, taskId, assignedAgentId, repositoryId, prompt, createPr, null);
    }

    public TaskExecutionRunCommand {
        workspaceId = requireId(workspaceId, "workspaceId");
        taskId = requireId(taskId, "taskId");
        assignedAgentId = requireOptionalId(assignedAgentId, "assignedAgentId");
        repositoryId = requireOptionalId(repositoryId, "repositoryId");
        prompt = requireNotBlank(prompt, "prompt");
        openClawSessionKeyOverride = normalizeOptional(
                openClawSessionKeyOverride,
                "openClawSessionKeyOverride",
                OPEN_CLAW_SESSION_KEY_MAX_LENGTH);
    }

    private static Long requireId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static Long requireOptionalId(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        return requireId(value, fieldName);
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be less than or equal to " + maxLength);
        }
        return trimmed;
    }
}
