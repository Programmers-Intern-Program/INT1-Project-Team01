package back.domain.gateway.client;

record OpenClawGatewayDeviceIdentity(String id, String publicKey, String privateKeyPem) {

    OpenClawGatewayDeviceIdentity {
        id = requireNotBlank(id, "id");
        publicKey = requireNotBlank(publicKey, "publicKey");
        privateKeyPem = requireNotBlank(privateKeyPem, "privateKeyPem");
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
