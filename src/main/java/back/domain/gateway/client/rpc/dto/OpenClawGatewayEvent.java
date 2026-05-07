package back.domain.gateway.client.rpc.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Record stores defensive Map copies and exposes immutable maps.")
public record OpenClawGatewayEvent(
        String type,
        String event,
        Map<String, Object> payload
) {

    public OpenClawGatewayEvent {
        if (payload == null) {
            payload = Map.of();
        } else {
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    public static OpenClawGatewayEvent of(String event, Map<String, Object> payload) {
        return new OpenClawGatewayEvent("event", event, payload);
    }
}
