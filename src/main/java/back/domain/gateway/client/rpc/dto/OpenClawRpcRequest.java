package back.domain.gateway.client.rpc.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Record stores defensive Map copies and exposes immutable maps."
)
public record OpenClawRpcRequest(
        String type,
        String id,
        String method,
        Map<String, Object> params
) {

    public OpenClawRpcRequest {
        params = normalizeParams(params);
    }

    public static OpenClawRpcRequest of(String id, String method, Map<String, Object> params) {
        return new OpenClawRpcRequest(
                "req",
                requireNotBlank(id, "id"),
                requireNotBlank(method, "method"),
                normalizeParams(params)
        );
    }

    private static Map<String, Object> normalizeParams(Map<String, Object> params) {
        if (params == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
