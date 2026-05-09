package back.domain.chat.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatAgentIntentParser {

    private static final String DEFAULT_TASK_MESSAGE = "작업을 시작하겠습니다.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatAgentIntent parse(String rawText) {
        String fallbackMessage = normalizeMessage(rawText);
        if (!looksLikeJson(fallbackMessage)) {
            return ChatAgentIntent.chat(fallbackMessage);
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(fallbackMessage);
        } catch (JsonProcessingException exception) {
            return ChatAgentIntent.chat(fallbackMessage);
        }

        if (!rootNode.isObject()) {
            return ChatAgentIntent.chat(fallbackMessage);
        }

        String intent = optionalText(rootNode, "intent");
        if (!"TASK".equalsIgnoreCase(intent)) {
            return ChatAgentIntent.chat(resolveMessage(rootNode, fallbackMessage));
        }

        return ChatAgentIntent.task(
                resolveMessage(rootNode, DEFAULT_TASK_MESSAGE),
                parseTaskSpec(rootNode.path("task")));
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

    private boolean looksLikeJson(String value) {
        return value.startsWith("{") && value.endsWith("}");
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
}
