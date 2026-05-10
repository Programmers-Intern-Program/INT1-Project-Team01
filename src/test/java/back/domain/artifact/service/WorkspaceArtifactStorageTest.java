package back.domain.artifact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.artifact.dto.StoredArtifactFile;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class WorkspaceArtifactStorageTest {

    @TempDir
    private Path tempDir;

    private final WorkspaceArtifactStorage storage = new WorkspaceArtifactStorage();

    @Test
    @DisplayName("workspace project root 아래에 Agent 파일 산출물을 저장한다")
    void storeFiles_success() throws Exception {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ArtifactFileSaveCommand file =
                new ArtifactFileSaveCommand("src/main/java/App.java", "class App {}\n");

        // when
        List<StoredArtifactFile> stored = storage.storeFiles(1L, List.of(file));

        // then
        Path savedPath = tempDir.resolve("workspaces/1/project/src/main/java/App.java");
        assertThat(Files.readString(savedPath)).isEqualTo("class App {}\n");
        assertThat(stored)
                .extracting(StoredArtifactFile::relativePath)
                .containsExactly("src/main/java/App.java");
    }

    @Test
    @DisplayName("project root 밖으로 나가는 경로는 차단한다")
    void storeFiles_pathTraversal_throwsException() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ArtifactFileSaveCommand file = new ArtifactFileSaveCommand("../secret.txt", "secret");

        // when & then
        assertThatThrownBy(() -> storage.storeFiles(1L, List.of(file)))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(Files.exists(tempDir.resolve("secret.txt"))).isFalse();
    }

    @Test
    @DisplayName("허용 크기를 초과한 파일은 저장하지 않는다")
    void storeFiles_tooLarge_throwsException() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(storage, "maxFileSizeBytes", 3L);
        ArtifactFileSaveCommand file = new ArtifactFileSaveCommand("result.txt", "1234");

        // when & then
        assertThatThrownBy(() -> storage.storeFiles(1L, List.of(file)))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(Files.exists(tempDir.resolve("workspaces/1/project/result.txt"))).isFalse();
    }

    @Test
    @DisplayName("모든 파일을 먼저 검증하므로 뒤 파일이 잘못되어도 앞 파일을 쓰지 않는다")
    void storeFiles_invalidLaterFile_doesNotWriteEarlierFile() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ArtifactFileSaveCommand validFile = new ArtifactFileSaveCommand("first.txt", "first");
        ArtifactFileSaveCommand invalidFile = new ArtifactFileSaveCommand("../secret.txt", "secret");

        // when & then
        assertThatThrownBy(() -> storage.storeFiles(1L, List.of(validFile, invalidFile)))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(Files.exists(tempDir.resolve("workspaces/1/project/first.txt"))).isFalse();
    }

    @Test
    @DisplayName("저장 중 실패하면 이미 저장한 파일을 정리한다")
    void storeFiles_writeFailure_cleansUpWrittenFiles() throws Exception {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        Path projectRoot = tempDir.resolve("workspaces/1/project");
        Files.createDirectories(projectRoot.resolve("second.txt"));
        ArtifactFileSaveCommand firstFile = new ArtifactFileSaveCommand("first.txt", "first");
        ArtifactFileSaveCommand secondFile = new ArtifactFileSaveCommand("second.txt", "second");

        // when & then
        assertThatThrownBy(() -> storage.storeFiles(1L, List.of(firstFile, secondFile)))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(Files.exists(projectRoot.resolve("first.txt"))).isFalse();
    }
}
