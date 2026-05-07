package back.domain.execution.service;

import back.domain.execution.entity.TaskExecutionStatus;
import java.util.Locale;

public enum AgentExecutionStatus {
    COMPLETED(TaskExecutionStatus.SUCCEEDED),
    FAILED(TaskExecutionStatus.FAILED),
    CANCELED(TaskExecutionStatus.CANCELED);

    private final TaskExecutionStatus taskExecutionStatus;

    AgentExecutionStatus(TaskExecutionStatus taskExecutionStatus) {
        this.taskExecutionStatus = taskExecutionStatus;
    }

    public static AgentExecutionStatus from(String value) {
        if (value == null || value.isBlank()) {
            return COMPLETED;
        }
        String normalized = normalize(value);
        return switch (normalized) {
            case "COMPLETED", "SUCCEEDED", "SUCCESS", "DONE", "PASSED" -> COMPLETED;
            case "FAILED", "FAILURE", "ERROR" -> FAILED;
            case "CANCELED", "CANCELLED" -> CANCELED;
            default -> fromUnknownStatus(normalized);
        };
    }

    public TaskExecutionStatus taskExecutionStatus() {
        return taskExecutionStatus;
    }

    public String reportStatus() {
        return name();
    }

    private static AgentExecutionStatus fromUnknownStatus(String normalized) {
        if (normalized.contains("FAIL") || normalized.contains("ERROR")) {
            return FAILED;
        }
        if (normalized.contains("CANCEL")) {
            return CANCELED;
        }
        if (normalized.contains("COMPLETE") || normalized.contains("SUCCESS") || normalized.contains("SUCCEED")) {
            return COMPLETED;
        }
        return FAILED;
    }

    private static String normalize(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
