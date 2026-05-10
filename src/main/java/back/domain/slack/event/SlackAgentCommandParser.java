package back.domain.slack.event;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SlackAgentCommandParser {

    private static final Pattern AGENT_COMMAND_PATTERN =
            Pattern.compile("^/agent\\s+(\\S+)(?:\\s+(.*))?$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public ParsedCommand parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParsedCommand(null, "");
        }

        String normalizedText = text.strip();
        Matcher matcher = AGENT_COMMAND_PATTERN.matcher(normalizedText);
        if (!matcher.matches()) {
            return new ParsedCommand(null, normalizedText);
        }

        String agentName = matcher.group(1).strip();
        String message = matcher.group(2) == null ? "" : matcher.group(2).strip();
        return new ParsedCommand(agentName, message);
    }

    public record ParsedCommand(String agentName, String message) {}
}
