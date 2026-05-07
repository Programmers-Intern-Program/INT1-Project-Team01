package back.domain.execution.dto.request;

public record AgentReportSaveRequest(String status, String summary, String detail, String recommendedAction) {

    public AgentReportSaveRequest {
        status = requireNotBlank(status, "status");
        summary = requireNotBlank(summary, "summary");
        detail = normalizeOptional(detail);
        recommendedAction = normalizeOptional(recommendedAction);
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
