package back.domain.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import back.domain.agent.entity.AgentCategory;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatAgentIntentParser {

    private static final String DEFAULT_TASK_MESSAGE = "작업을 시작하겠습니다.";
    private static final Pattern JSON_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatAgentIntent parse(String rawText) {
        String fallbackMessage = normalizeMessage(rawText);
        return parseFirstJson(fallbackMessage)
                .map(node -> parseJsonIntent(node, fallbackMessage))
                .orElseGet(() -> ChatAgentIntent.chat(fallbackMessage));
    }

    private ChatAgentIntent parseJsonIntent(JsonNode rootNode, String fallbackMessage) {
        String intent = optionalText(rootNode, "intent");
        if ("TASK".equalsIgnoreCase(intent)) {
            return ChatAgentIntent.task(
                    resolveMessage(rootNode, DEFAULT_TASK_MESSAGE),
                    parseTaskSpec(rootNode.path("task")));
        }
        if ("ORCHESTRATE".equalsIgnoreCase(intent)) {
            return ChatAgentIntent.orchestrate(
                    resolveMessage(rootNode, "작업 계획을 수립했습니다."),
                    parseOrchestrationPlanSpec(rootNode.path("plan")));
        }
        if (!"CHAT".equalsIgnoreCase(intent)) {
            return ChatAgentIntent.chat(resolveMessage(rootNode, fallbackMessage));
        }
        return ChatAgentIntent.chat(resolveMessage(rootNode, fallbackMessage));
    }

    private ChatAgentIntent.TaskSpec parseTaskSpec(JsonNode taskNode) {
        return new ChatAgentIntent.TaskSpec(
                optionalText(taskNode, "title"),
                optionalText(taskNode, "description"),
                optionalEnum(taskNode, "taskType", TaskType.class),
                optionalEnum(taskNode, "priority", TaskPriority.class),
                optionalPositiveLong(taskNode, "repositoryId"),
                optionalBoolean(taskNode, "createPr").orElse(null));
    }

    private ChatAgentIntent.OrchestrationPlanSpec parseOrchestrationPlanSpec(JsonNode planNode) {
        return new ChatAgentIntent.OrchestrationPlanSpec(
                optionalText(planNode, "title"),
                parseStepSpecs(planNode.path("steps")));
    }

    private List<ChatAgentIntent.OrchestrationStepSpec> parseStepSpecs(JsonNode stepsNode) {
        if (!stepsNode.isArray()) {
            return List.of();
        }
        List<ChatAgentIntent.OrchestrationStepSpec> steps = new ArrayList<>();
        for (JsonNode stepNode : stepsNode) {
            steps.add(new ChatAgentIntent.OrchestrationStepSpec(
                    optionalText(stepNode, "stepKey"),
                    optionalPositiveLong(stepNode, "agentId"),
                    optionalText(stepNode, "agentName"),
                    optionalEnum(stepNode, "category", AgentCategory.class),
                    optionalText(stepNode, "title"),
                    optionalText(stepNode, "prompt"),
                    optionalTextList(stepNode.path("dependsOn"))));
        }
        return steps;
    }

    private String resolveMessage(JsonNode rootNode, String fallbackMessage) {
        String message = optionalText(rootNode, "message");
        if (message == null) {
            return fallbackMessage;
        }
        return message;
    }

    private String normalizeMessage(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        return rawText.trim();
    }

    private Optional<JsonNode> parseFirstJson(String rawText) {
        for (String candidate : jsonCandidates(rawText)) {
            Optional<JsonNode> parsed = parseJson(candidate);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private Set<String> jsonCandidates(String rawText) {
        Set<String> candidates = new LinkedHashSet<>();
        if (!rawText.isBlank()) {
            candidates.add(rawText);
        }
        Matcher matcher = JSON_FENCE.matcher(rawText);
        while (matcher.find()) {
            candidates.add(matcher.group(1).trim());
        }
        int firstBrace = rawText.indexOf('{');
        int lastBrace = rawText.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            candidates.add(rawText.substring(firstBrace, lastBrace + 1).trim());
        }
        return candidates;
    }

    private Optional<JsonNode> parseJson(String value) {
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node != null && node.isObject()) {
                return Optional.of(node);
            }
            return Optional.empty();
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private String optionalText(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private <E extends Enum<E>> E optionalEnum(JsonNode node, String fieldName, Class<E> enumType) {
        String value = optionalText(node, fieldName);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Long optionalPositiveLong(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        JsonNode valueNode = node.get(fieldName);
        Long value = null;
        if (valueNode.canConvertToLong()) {
            value = valueNode.asLong();
        } else if (valueNode.isTextual()) {
            value = parseLong(valueNode.asText());
        }
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Optional<Boolean> optionalBoolean(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return Optional.empty();
        }
        JsonNode valueNode = node.get(fieldName);
        if (valueNode.isBoolean()) {
            return Optional.of(valueNode.asBoolean());
        }
        if (valueNode.isTextual()) {
            String value = valueNode.asText().trim();
            if ("true".equalsIgnoreCase(value)) {
                return Optional.of(true);
            }
            if ("false".equalsIgnoreCase(value)) {
                return Optional.of(false);
            }
        }
        return Optional.empty();
    }

    private List<String> optionalTextList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode valueNode : node) {
            if (valueNode == null || valueNode.isNull()) {
                continue;
            }
            String value = valueNode.asText();
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }
}
