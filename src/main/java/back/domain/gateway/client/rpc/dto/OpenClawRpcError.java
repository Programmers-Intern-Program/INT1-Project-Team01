package back.domain.gateway.client.rpc.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Map;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Record stores defensive Map copies and exposes immutable maps."
)
public record OpenClawRpcError(
        @JsonAlias("errorCode") String code,
        @JsonAlias("error") String message,
        String requestId,
        Map<String, Object> details
) {

    public OpenClawRpcError(String code, String message) {
        this(code, message, null, Map.of());
    }

    public OpenClawRpcError {
        if (details == null) {
            details = Map.of();
        } else {
            details = Map.copyOf(details);
        }
    }
}
