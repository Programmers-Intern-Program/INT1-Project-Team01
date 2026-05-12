package back.domain.gateway.client;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenClawGatewayRpcClientFactory implements OpenClawGatewayClientFactory {

    private final Duration rpcTimeout;
    private final Path deviceIdentityDirectory;
    private final Duration remoteConnectChallengeWait;

    public OpenClawGatewayRpcClientFactory(
            @Value("${openclaw.gateway.rpc-timeout:10s}") Duration rpcTimeout,
            @Value("${openclaw.gateway.device-identity-dir:}") String deviceIdentityDirectory,
            @Value("${openclaw.gateway.remote-connect-challenge-wait:750ms}") Duration remoteConnectChallengeWait) {
        this.rpcTimeout = rpcTimeout;
        this.deviceIdentityDirectory = resolveDeviceIdentityDirectory(deviceIdentityDirectory);
        this.remoteConnectChallengeWait = remoteConnectChallengeWait;
    }

    @Override
    public OpenClawGatewayClient create() {
        return OpenClawGatewayRpcClient.webSocket(rpcTimeout, deviceIdentityDirectory, remoteConnectChallengeWait);
    }

    private Path resolveDeviceIdentityDirectory(String configuredDirectory) {
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            return OpenClawGatewayDeviceIdentityStore.defaultDirectory();
        }
        return Path.of(configuredDirectory.trim());
    }
}
