package back.domain.gateway.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class OpenClawGatewayDeviceAuthenticator {

    private static final String SIGNATURE_VERSION = "v2";

    private final OpenClawGatewayDeviceIdentityStore identityStore;

    OpenClawGatewayDeviceAuthenticator(OpenClawGatewayDeviceIdentityStore identityStore) {
        this.identityStore = Objects.requireNonNull(identityStore);
    }

    static OpenClawGatewayDeviceAuthenticator defaultAuthenticator() {
        return new OpenClawGatewayDeviceAuthenticator(OpenClawGatewayDeviceIdentityStore.defaultStore());
    }

    Map<String, Object> buildDeviceAuth(
            String gatewayUrl,
            String token,
            Map<String, Object> challenge,
            String clientId,
            String clientMode,
            String role,
            List<String> scopes) {
        Object nonce = requireChallengeValue(challenge, "nonce");
        Object signedAt = requireChallengeValue(challenge, "ts");
        OpenClawGatewayDeviceIdentity identity = identityStore.loadOrCreate(gatewayUrl);
        String payload = String.join(
                "|",
                SIGNATURE_VERSION,
                identity.id(),
                clientId,
                clientMode,
                role,
                String.join(",", scopes),
                String.valueOf(signedAt),
                token,
                String.valueOf(nonce));

        Map<String, Object> device = new LinkedHashMap<>();
        device.put("id", identity.id());
        device.put("publicKey", identity.publicKey());
        device.put("signature", sign(payload, identity.privateKeyPem()));
        device.put("signedAt", signedAt);
        device.put("nonce", nonce);
        return device;
    }

    private Object requireChallengeValue(Map<String, Object> challenge, String fieldName) {
        if (challenge == null || !challenge.containsKey(fieldName) || challenge.get(fieldName) == null) {
            throw new IllegalArgumentException("connect.challenge " + fieldName + " must not be null");
        }
        return challenge.get(fieldName);
    }

    private String sign(String payload, String privateKeyPem) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(toPrivateKey(privateKeyPem));
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("OpenClaw device signature generation failed", exception);
        }
    }

    private PrivateKey toPrivateKey(String privateKeyPem) throws GeneralSecurityException {
        String encoded = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
