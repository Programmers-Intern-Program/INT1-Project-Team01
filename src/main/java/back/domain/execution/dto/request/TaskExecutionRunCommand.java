package back.domain.execution.dto.request;

public record TaskExecutionRunCommand(
        Long workspaceId, Long taskId, Long repositoryId, String prompt, boolean createPr) {

    public TaskExecutionRunCommand {
        workspaceId = requireId(workspaceId, "workspaceId");
        taskId = requireId(taskId, "taskId");
        repositoryId = requireOptionalId(repositoryId, "repositoryId");
        prompt = requireNotBlank(prompt, "prompt");
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
}
