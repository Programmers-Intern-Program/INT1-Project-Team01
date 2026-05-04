package back.domain.gateway.client.rpc.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Map;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Record stores defensive Map copies and exposes immutable maps."
)
public record OpenClawRpcResponse(
        String type,
        String id,
        boolean ok,
        Map<String, Object> payload,
        OpenClawRpcError error
) {

    public OpenClawRpcResponse {
        if (payload != null) {
            payload = Map.copyOf(payload);
        }
    }

    public static OpenClawRpcResponse success(String id, Map<String, Object> payload) {
        return new OpenClawRpcResponse(
                "res",
                requireNotBlank(id, "id"),
                true,
                normalizePayload(payload),
                null
        );
    }

    public static OpenClawRpcResponse failure(String id, OpenClawRpcError error) {
        if (error == null) {
            throw new IllegalArgumentException("error must not be null");
        }
        return new OpenClawRpcResponse(
                "res",
                requireNotBlank(id, "id"),
                false,
                null,
                error
        );
    }

    private static Map<String, Object> normalizePayload(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        return Map.copyOf(payload);
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
