package back.domain.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenClawGatewayDeviceIdentityStoreTest {

    @Test
    @DisplayName("device identity는 Gateway별 파일로 저장하고 재사용한다")
    void loadOrCreate_reusesPersistedIdentity(@TempDir Path tempDir) {
        // given
        OpenClawGatewayDeviceIdentityStore store = new OpenClawGatewayDeviceIdentityStore(tempDir);

        // when
        OpenClawGatewayDeviceIdentity first = store.loadOrCreate("wss://gateway.example.test");
        OpenClawGatewayDeviceIdentity second = store.loadOrCreate("wss://gateway.example.test/");

        // then
        assertThat(second).isEqualTo(first);
        assertThat(identityFiles(tempDir)).hasSize(1);
    }

    @Test
    @DisplayName("저장된 device identity 파일이 손상되면 조용히 재생성하지 않고 실패한다")
    void loadOrCreate_invalidIdentityFile_throwsException(@TempDir Path tempDir) throws IOException {
        // given
        OpenClawGatewayDeviceIdentityStore store = new OpenClawGatewayDeviceIdentityStore(tempDir);
        store.loadOrCreate("wss://gateway.example.test");
        Path identityFile = identityFiles(tempDir).getFirst();
        Files.writeString(
                identityFile,
                "invalid-json",
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);

        // when & then
        assertThatThrownBy(() -> store.loadOrCreate("wss://gateway.example.test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenClaw device identity file is invalid");
    }

    private List<Path> identityFiles(Path directory) {
        try (var files = Files.list(directory)) {
            return files.toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
