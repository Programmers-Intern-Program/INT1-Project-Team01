package back.domain.execution.service;

import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentExecutionResultParser {

    private static final String DEFAULT_SUCCESS_STATUS = "COMPLETED";
    private static final String DEFAULT_SUCCESS_SUMMARY = "Agent 실행이 완료되었습니다.";
    private static final Pattern JSON_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentExecutionResult parse(String finalText) {
        String normalizedFinalText = normalize(finalText);
        return parseFirstJson(normalizedFinalText)
                .map(node -> parseJsonReport(node, normalizedFinalText))
                .orElseGet(() -> fallbackReport(normalizedFinalText));
    }

    private AgentExecutionResult parseJsonReport(JsonNode root, String finalText) {
        JsonNode reportNode = root.path("report").isObject() ? root.path("report") : root;
        String status = firstText(reportNode, "status").orElse(DEFAULT_SUCCESS_STATUS);
        String summary = firstText(reportNode, "summary").orElseGet(() -> fallbackSummary(finalText));
        String detail = firstText(reportNode, "detail").orElse(finalText);
        String recommendedAction = firstText(reportNode, "recommendedAction", "recommended_action").orElse(null);
        return new AgentExecutionResult(
                new AgentReportSaveRequest(status, summary, detail, recommendedAction),
                parseArtifacts(root, reportNode));
    }

    private AgentExecutionResult fallbackReport(String finalText) {
        return new AgentExecutionResult(
                new AgentReportSaveRequest(
                        DEFAULT_SUCCESS_STATUS,
                        fallbackSummary(finalText),
                        finalText.isBlank() ? null : finalText,
                        null),
                List.of());
    }

    private List<TaskArtifactSaveRequest> parseArtifacts(JsonNode root, JsonNode reportNode) {
        JsonNode artifactsNode = root.path("artifacts").isArray()
                ? root.path("artifacts")
                : reportNode.path("artifacts");
        if (!artifactsNode.isArray()) {
            return List.of();
        }
        List<TaskArtifactSaveRequest> artifacts = new ArrayList<>();
        artifactsNode.forEach(artifactNode -> parseArtifact(artifactNode).ifPresent(artifacts::add));
        return artifacts;
    }

    private Optional<TaskArtifactSaveRequest> parseArtifact(JsonNode artifactNode) {
        if (!artifactNode.isObject()) {
            return Optional.empty();
        }
        Optional<String> artifactType = firstText(artifactNode, "artifactType", "type");
        Optional<String> name = firstText(artifactNode, "name");
        if (artifactType.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }
        String url = firstText(artifactNode, "url", "value", "path").orElse(null);
        return Optional.of(new TaskArtifactSaveRequest(artifactType.get(), name.get(), url));
    }

    private Optional<JsonNode> parseFirstJson(String finalText) {
        for (String candidate : jsonCandidates(finalText)) {
            Optional<JsonNode> parsed = parseJson(candidate);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private Set<String> jsonCandidates(String finalText) {
        Set<String> candidates = new LinkedHashSet<>();
        if (!finalText.isBlank()) {
            candidates.add(finalText);
        }
        Matcher matcher = JSON_FENCE.matcher(finalText);
        while (matcher.find()) {
            candidates.add(matcher.group(1).trim());
        }
        int firstBrace = finalText.indexOf('{');
        int lastBrace = finalText.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            candidates.add(finalText.substring(firstBrace, lastBrace + 1).trim());
        }
        return candidates;
    }

    private Optional<JsonNode> parseJson(String value) {
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return Optional.of(value.asText().trim());
            }
        }
        return Optional.empty();
    }

    private String fallbackSummary(String finalText) {
        if (finalText.isBlank()) {
            return DEFAULT_SUCCESS_SUMMARY;
        }
        return finalText;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
