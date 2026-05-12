package back.domain.gateway.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenClawGatewayDeviceIdentityStore {

    private static final String DEVICES_DIR_NAME = "openclaw-devices";
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path devicesDirectory;

    OpenClawGatewayDeviceIdentityStore(Path devicesDirectory) {
        this.devicesDirectory = Objects.requireNonNull(devicesDirectory);
    }

    static OpenClawGatewayDeviceIdentityStore defaultStore() {
        String home = System.getProperty("user.home", ".");
        return new OpenClawGatewayDeviceIdentityStore(Path.of(home, ".ai-office", DEVICES_DIR_NAME));
    }

    OpenClawGatewayDeviceIdentity loadOrCreate(String identityKey) {
        Path identityPath = devicesDirectory.resolve(hash(normalizeIdentityKey(identityKey)) + ".json");
        try {
            Path parentDirectory = identityPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            return readIdentity(identityPath).orElseGet(() -> writeNewIdentity(identityPath));
        } catch (IOException exception) {
            return generateIdentity();
        }
    }

    private Optional<OpenClawGatewayDeviceIdentity> readIdentity(Path identityPath) throws IOException {
        if (!Files.exists(identityPath)) {
            return Optional.empty();
        }
        try {
            DeviceIdentityPayload payload =
                    OBJECT_MAPPER.readValue(Files.readString(identityPath, StandardCharsets.UTF_8),
                            DeviceIdentityPayload.class);
            if (payload.id == null || payload.publicKey == null || payload.privateKeyPem == null) {
                return Optional.empty();
            }
            return Optional.of(new OpenClawGatewayDeviceIdentity(payload.id, payload.publicKey, payload.privateKeyPem));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private OpenClawGatewayDeviceIdentity writeNewIdentity(Path identityPath) {
        OpenClawGatewayDeviceIdentity identity = generateIdentity();
        try {
            Map<String, String> payload = Map.of(
                    "id", identity.id(),
                    "publicKey", identity.publicKey(),
                    "privateKeyPem", identity.privateKeyPem(),
                    "createdAt", Instant.now().toString());
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload) + "\n";
            Files.writeString(
                    identityPath,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            restrictOwnerAccess(identityPath);
        } catch (IOException exception) {
            return identity;
        }
        return identity;
    }

    private OpenClawGatewayDeviceIdentity generateIdentity() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair keyPair = generator.generateKeyPair();
            byte[] publicRaw = extractRawEd25519PublicKey(keyPair.getPublic().getEncoded());
            String publicKey = base64Url(publicRaw);
            String id = hash(publicRaw);
            String privateKeyPem = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
            return new OpenClawGatewayDeviceIdentity(id, publicKey, privateKeyPem);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Ed25519 device identity generation failed", exception);
        }
    }

    private static byte[] extractRawEd25519PublicKey(byte[] spkiDer) {
        byte[] raw = new byte[32];
        System.arraycopy(spkiDer, spkiDer.length - raw.length, raw, 0, raw.length);
        return raw;
    }

    private static String normalizeIdentityKey(String identityKey) {
        return identityKey == null ? "" : identityKey.trim().replaceAll("/+$", "");
    }

    private static String hash(String value) {
        return hash(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String toPem(String type, byte[] der) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----";
    }

    private static void restrictOwnerAccess(Path identityPath) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    identityPath,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException exception) {
            // Non-POSIX filesystems keep the process default permissions.
        }
    }

    private static class DeviceIdentityPayload {
        public String id;
        public String publicKey;
        public String privateKeyPem;
    }
}
