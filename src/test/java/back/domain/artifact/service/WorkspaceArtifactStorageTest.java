package back.domain.artifact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.artifact.dto.ArtifactFileContent;
import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.artifact.dto.ArtifactTree;
import back.domain.artifact.dto.ArtifactTreeNode;
import back.domain.artifact.dto.ArtifactTreeNodeType;
import back.domain.artifact.dto.StoredArtifactFile;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;

class WorkspaceArtifactStorageTest {

    @TempDir
    private Path tempDir;

    private final WorkspaceArtifactStorage storage = new WorkspaceArtifactStorage();

    @Test
    @DisplayName("workspace project root 아래에 Agent 파일 산출물을 저장한다")
    void storeFiles_success() throws Exception {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ArtifactFileSaveCommand file = new ArtifactFileSaveCommand("src/main/java/App.java", "class App {}\n");

        // when
        List<StoredArtifactFile> stored = storage.storeFiles(1L, List.of(file));

        // then
        Path savedPath = tempDir.resolve("workspaces/1/project/src/main/java/App.java");
        assertThat(Files.readString(savedPath)).isEqualTo("class App {}\n");
        assertThat(stored).extracting(StoredArtifactFile::relativePath).containsExactly("src/main/java/App.java");
    }

    @Test
    @DisplayName("workspace project root 파일 트리를 디렉터리 우선으로 조회한다")
    void listProjectTree_success() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        storage.storeFiles(
                1L,
                List.of(
                        new ArtifactFileSaveCommand("README.md", "# Result\n"),
                        new ArtifactFileSaveCommand("src/main/java/App.java", "class App {}\n")));

        // when
        ArtifactTree tree = storage.listProjectTree(1L);

        // then
        assertThat(tree.children()).extracting(ArtifactTreeNode::name).containsExactly("src", "README.md");
        ArtifactTreeNode src = tree.children().getFirst();
        assertThat(src.type()).isEqualTo(ArtifactTreeNodeType.DIRECTORY);
        assertThat(src.children().getFirst().path()).isEqualTo("src/main");
        ArtifactTreeNode readme = tree.children().get(1);
        assertThat(readme.type()).isEqualTo(ArtifactTreeNodeType.FILE);
        assertThat(readme.contentType()).isEqualTo("text/markdown");
        assertThat(readme.sizeBytes()).isPositive();
    }

    @Test
    @DisplayName("파일 트리는 차단 확장자 파일을 노출하지 않는다")
    void listProjectTree_blockedExtension_excludesFile() throws Exception {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        Path projectRoot = tempDir.resolve("workspaces/1/project");
        Files.createDirectories(projectRoot);
        Files.writeString(projectRoot.resolve("README.md"), "# Result\n", StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("agent.jar"), "binary", StandardCharsets.UTF_8);

        // when
        ArtifactTree tree = storage.listProjectTree(1L);

        // then
        assertThat(tree.children()).extracting(ArtifactTreeNode::path).containsExactly("README.md");
    }

    @Test
    @DisplayName("파일 트리는 최대 depth 이후 하위 노드를 펼치지 않는다")
    void listProjectTree_maxDepth_stopsRecursion() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(storage, "maxTreeDepth", 1);
        storage.storeFiles(1L, List.of(new ArtifactFileSaveCommand("src/main/java/App.java", "class App {}\n")));

        // when
        ArtifactTree tree = storage.listProjectTree(1L);

        // then
        ArtifactTreeNode src = tree.children().getFirst();
        assertThat(src.path()).isEqualTo("src");
        assertThat(src.children()).isEmpty();
    }

    @Test
    @DisplayName("파일 트리가 허용 노드 수를 초과하면 조회를 중단한다")
    void listProjectTree_tooManyNodes_throwsException() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(storage, "maxTreeNodes", 1);
        storage.storeFiles(
                1L,
                List.of(
                        new ArtifactFileSaveCommand("a.txt", "a"),
                        new ArtifactFileSaveCommand("b.txt", "b")));

        // when & then
        assertThatThrownBy(() -> storage.listProjectTree(1L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("산출물 파일 내용을 UTF-8 텍스트와 contentType으로 조회한다")
    void readFile_success() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        storage.storeFiles(1L, List.of(new ArtifactFileSaveCommand("src/main/java/App.java", "class App {}\n")));

        // when
        ArtifactFileContent content = storage.readFile(1L, "src/main/java/App.java");

        // then
        assertThat(content.path()).isEqualTo("src/main/java/App.java");
        assertThat(content.name()).isEqualTo("App.java");
        assertThat(content.contentType()).isEqualTo("text/x-java-source");
        assertThat(content.content()).isEqualTo("class App {}\n");
        assertThat(content.sizeBytes()).isEqualTo("class App {}\n".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("파일 조회에서도 차단 확장자는 읽지 않는다")
    void readFile_blockedExtension_throwsException() throws Exception {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        Path projectRoot = tempDir.resolve("workspaces/1/project");
        Files.createDirectories(projectRoot);
        Files.writeString(projectRoot.resolve("agent.jar"), "binary", StandardCharsets.UTF_8);

        // when & then
        assertThatThrownBy(() -> storage.readFile(1L, "agent.jar"))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> storage.describeFile(1L, "agent.jar"))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("파일 조회에서도 project root 밖으로 나가는 경로는 차단한다")
    void readFile_pathTraversal_throwsException() {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());

        // when & then
        assertThatThrownBy(() -> storage.readFile(1L, "../secret.txt"))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
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
    @DisplayName("기존 parent 경로가 symlink이면 project root 밖에 파일을 쓰지 않는다")
    void storeFiles_symlinkParent_throwsException() throws Exception {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        Path projectRoot = tempDir.resolve("workspaces/1/project");
        Path outsideDir = tempDir.resolve("outside");
        Files.createDirectories(projectRoot);
        Files.createDirectories(outsideDir);
        Files.createSymbolicLink(projectRoot.resolve("linked"), outsideDir);

        // when & then
        ArtifactFileSaveCommand file = new ArtifactFileSaveCommand("linked/result.txt", "result");
        assertThatThrownBy(() -> storage.storeFiles(1L, List.of(file)))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(Files.exists(outsideDir.resolve("result.txt"))).isFalse();
    }

    @Test
    @DisplayName("기존 target 파일이 symlink이면 외부 파일을 덮어쓰지 않는다")
    void storeFiles_symlinkTarget_throwsException() throws Exception {
        // given
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        Path projectRoot = tempDir.resolve("workspaces/1/project");
        Path outsideFile = tempDir.resolve("outside.txt");
        Files.createDirectories(projectRoot);
        Files.writeString(outsideFile, "outside", StandardCharsets.UTF_8);
        Files.createSymbolicLink(projectRoot.resolve("result.txt"), outsideFile);

        // when & then
        ArtifactFileSaveCommand file = new ArtifactFileSaveCommand("result.txt", "changed");
        assertThatThrownBy(() -> storage.storeFiles(1L, List.of(file)))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(Files.readString(outsideFile, StandardCharsets.UTF_8)).isEqualTo("outside");
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
        assertThat(Files.exists(tempDir.resolve("workspaces/1/project/result.txt")))
                .isFalse();
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
        assertThat(Files.exists(tempDir.resolve("workspaces/1/project/first.txt")))
                .isFalse();
    }

    @Test
    @DisplayName("저장 대상이 일반 파일이 아니면 쓰기 전 차단한다")
    void storeFiles_nonRegularTarget_throwsExceptionBeforeWrite() throws Exception {
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
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(Files.exists(projectRoot.resolve("first.txt"))).isFalse();
    }
}
