package back.domain.artifact.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import back.domain.artifact.dto.ArtifactFileContent;
import back.domain.artifact.dto.ArtifactFileReference;
import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.artifact.dto.ArtifactTree;
import back.domain.artifact.dto.ArtifactTreeNode;
import back.domain.artifact.dto.ArtifactTreeNodeType;
import back.domain.artifact.dto.StoredArtifactFile;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;

@Component
public class WorkspaceArtifactStorage {

    private static final String DEFAULT_BASE_PATH = "runtime-workspaces";
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_FILES_PER_RESULT = 50;
    private static final Set<String> BLOCKED_EXTENSIONS =
            Set.of(".class", ".jar", ".war", ".zip", ".exe", ".dll", ".dylib", ".so");
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry(".md", "text/markdown"),
            Map.entry(".markdown", "text/markdown"),
            Map.entry(".java", "text/x-java-source"),
            Map.entry(".kt", "text/x-kotlin-source"),
            Map.entry(".js", "text/javascript"),
            Map.entry(".ts", "text/typescript"),
            Map.entry(".jsx", "text/javascript"),
            Map.entry(".tsx", "text/typescript"),
            Map.entry(".json", "application/json"),
            Map.entry(".yml", "application/yaml"),
            Map.entry(".yaml", "application/yaml"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".html", "text/html"),
            Map.entry(".css", "text/css"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".properties", "text/plain"),
            Map.entry(".gradle", "text/plain"),
            Map.entry(".kts", "text/plain"));

    @Value("${app.artifact.base-path:runtime-workspaces}")
    private String basePath;

    @Value("${app.artifact.max-file-size-bytes:1048576}")
    private long maxFileSizeBytes;

    @Value("${app.artifact.max-files-per-result:50}")
    private int maxFilesPerResult;

    public List<StoredArtifactFile> storeFiles(Long workspaceId, List<ArtifactFileSaveCommand> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        requireWorkspaceId(workspaceId);
        validateFileCount(files);
        Path projectRoot = resolveProjectRoot(workspaceId);
        List<FileWritePlan> plans =
                files.stream().map(file -> toFileWritePlan(projectRoot, file)).toList();
        createDirectories(projectRoot);
        return writeFiles(plans);
    }

    public Path resolveProjectRoot(Long workspaceId) {
        requireWorkspaceId(workspaceId);
        return normalizedBasePath()
                .resolve("workspaces")
                .resolve(workspaceId.toString())
                .resolve("project")
                .normalize();
    }

    public ArtifactTree listProjectTree(Long workspaceId) {
        requireWorkspaceId(workspaceId);
        Path projectRoot = resolveProjectRoot(workspaceId);
        if (!Files.exists(projectRoot)) {
            return new ArtifactTree(List.of());
        }
        if (!Files.isDirectory(projectRoot)) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#listProjectTree] project root is not a directory. path=" + projectRoot,
                    "산출물 루트 디렉터리가 올바르지 않습니다.");
        }
        return new ArtifactTree(listChildren(projectRoot, projectRoot));
    }

    public ArtifactFileContent readFile(Long workspaceId, String path) {
        requireWorkspaceId(workspaceId);
        Path projectRoot = resolveProjectRoot(workspaceId);
        ArtifactPath resolved = resolveExistingFile(projectRoot, path);
        long sizeBytes = fileSize(resolved.target());
        validateFileSize(resolved.relativePath(), sizeBytes);
        return new ArtifactFileContent(
                toPortablePath(resolved.relativePath()),
                fileNameOf(resolved.relativePath()),
                resolveContentType(resolved.relativePath()),
                sizeBytes,
                readString(resolved.target()));
    }

    public ArtifactFileReference describeFile(Long workspaceId, String path) {
        requireWorkspaceId(workspaceId);
        Path projectRoot = resolveProjectRoot(workspaceId);
        Path relativePath = normalizeRelativePath(path);
        Path target = resolveWithinProjectRoot(projectRoot, relativePath, path);
        String portablePath = toPortablePath(relativePath);
        String fileName = fileNameOf(relativePath);
        String contentType = resolveContentType(relativePath);
        if (!Files.exists(target) || Files.isDirectory(target) || Files.isSymbolicLink(target)) {
            return new ArtifactFileReference(portablePath, fileName, contentType, null, false);
        }
        validateRealPath(projectRoot, target, path);
        return new ArtifactFileReference(portablePath, fileName, contentType, fileSize(target), true);
    }

    private FileWritePlan toFileWritePlan(Path projectRoot, ArtifactFileSaveCommand file) {
        Path relativePath = normalizeRelativePath(file.path());
        validateExtension(relativePath);
        int sizeBytes = file.content().getBytes(StandardCharsets.UTF_8).length;
        validateFileSize(relativePath, sizeBytes);
        Path target = resolveWithinProjectRoot(projectRoot, relativePath, file.path());
        return new FileWritePlan(relativePath, target, file.content(), sizeBytes);
    }

    private List<StoredArtifactFile> writeFiles(List<FileWritePlan> plans) {
        List<WriteSnapshot> snapshots = new ArrayList<>();
        try {
            for (FileWritePlan plan : plans) {
                createDirectories(Objects.requireNonNull(plan.target().getParent(), "target parent must not be null"));
                WriteSnapshot snapshot = createSnapshot(plan.target());
                snapshots.add(snapshot);
                writeFile(plan.target(), plan.content());
            }
            return plans.stream()
                    .map(plan -> new StoredArtifactFile(toPortablePath(plan.relativePath()), plan.sizeBytes()))
                    .toList();
        } catch (RuntimeException exception) {
            restoreSnapshots(snapshots);
            throw exception;
        }
    }

    private Path normalizeRelativePath(String value) {
        if (value == null || value.isBlank()) {
            throw invalidPath(value);
        }
        String normalizedValue = value.replace('\\', '/');
        if (normalizedValue.indexOf('\0') >= 0) {
            throw invalidPath(value);
        }
        Path path = Path.of(normalizedValue).normalize();
        String portablePath = toPortablePath(path);
        if (path.isAbsolute()
                || portablePath.isBlank()
                || ".".equals(portablePath)
                || startsWithParentReference(path)) {
            throw invalidPath(value);
        }
        return path;
    }

    private Path resolveWithinProjectRoot(Path projectRoot, Path relativePath, String originalPath) {
        Path target = projectRoot.resolve(relativePath).normalize();
        if (!target.startsWith(projectRoot)) {
            throw invalidPath(originalPath);
        }
        return target;
    }

    private ArtifactPath resolveExistingFile(Path projectRoot, String path) {
        Path relativePath = normalizeRelativePath(path);
        Path target = resolveWithinProjectRoot(projectRoot, relativePath, path);
        if (!Files.exists(target)) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "[WorkspaceArtifactStorage#resolveExistingFile] file not found. path=" + relativePath,
                    "산출물 파일을 찾을 수 없습니다.");
        }
        if (Files.isDirectory(target) || Files.isSymbolicLink(target)) {
            throw invalidPath(path);
        }
        validateRealPath(projectRoot, target, path);
        return new ArtifactPath(relativePath, target);
    }

    private void validateRealPath(Path projectRoot, Path target, String originalPath) {
        try {
            Path realProjectRoot = projectRoot.toRealPath().normalize();
            Path realTarget = target.toRealPath().normalize();
            if (!realTarget.startsWith(realProjectRoot)) {
                throw invalidPath(originalPath);
            }
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#validateRealPath] failed. path=" + target,
                    "산출물 파일 경로를 확인하지 못했습니다.");
        }
    }

    private boolean startsWithParentReference(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
    }

    private void validateExtension(Path relativePath) {
        String fileName = fileNameOf(relativePath).toLowerCase(Locale.ROOT);
        boolean blocked = BLOCKED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
        if (blocked) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[WorkspaceArtifactStorage#validateExtension] blocked file extension. path=" + relativePath,
                    "저장할 수 없는 파일 형식입니다.");
        }
    }

    private void validateFileSize(Path relativePath, long sizeBytes) {
        long limit = normalizedMaxFileSizeBytes();
        if (sizeBytes > limit) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[WorkspaceArtifactStorage#validateFileSize] file too large. path="
                            + relativePath
                            + ", sizeBytes="
                            + sizeBytes
                            + ", limit="
                            + limit,
                    "산출물 파일 크기가 허용 범위를 초과했습니다.");
        }
    }

    private List<ArtifactTreeNode> listChildren(Path projectRoot, Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> !Files.isSymbolicLink(path))
                    .sorted(this::compareTreePath)
                    .map(path -> toTreeNode(projectRoot, path))
                    .toList();
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#listChildren] failed. path=" + directory,
                    "산출물 파일 트리를 조회하지 못했습니다.");
        }
    }

    private int compareTreePath(Path left, Path right) {
        boolean leftDirectory = Files.isDirectory(left);
        boolean rightDirectory = Files.isDirectory(right);
        if (leftDirectory != rightDirectory) {
            return leftDirectory ? -1 : 1;
        }
        return Comparator.comparing(this::fileNameOf, String.CASE_INSENSITIVE_ORDER)
                .compare(left, right);
    }

    private ArtifactTreeNode toTreeNode(Path projectRoot, Path path) {
        Path relativePath = projectRoot.relativize(path);
        String portablePath = toPortablePath(relativePath);
        String name = fileNameOf(path);
        if (Files.isDirectory(path)) {
            return new ArtifactTreeNode(
                    name, portablePath, ArtifactTreeNodeType.DIRECTORY, null, null, listChildren(projectRoot, path));
        }
        return new ArtifactTreeNode(
                name,
                portablePath,
                ArtifactTreeNodeType.FILE,
                resolveContentType(relativePath),
                fileSize(path),
                List.of());
    }

    private String resolveContentType(Path relativePath) {
        String fileName = fileNameOf(relativePath).toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.entrySet().stream()
                .filter(entry -> fileName.endsWith(entry.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse("text/plain");
    }

    private void validateFileCount(List<ArtifactFileSaveCommand> files) {
        int limit = normalizedMaxFilesPerResult();
        if (files.size() > limit) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[WorkspaceArtifactStorage#validateFileCount] too many files. count="
                            + files.size()
                            + ", limit="
                            + limit,
                    "한 번에 저장할 수 있는 산출물 파일 수를 초과했습니다.");
        }
    }

    private Path normalizedBasePath() {
        String value = basePath == null || basePath.isBlank() ? DEFAULT_BASE_PATH : basePath.trim();
        return Path.of(value).toAbsolutePath().normalize();
    }

    private long normalizedMaxFileSizeBytes() {
        return maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    private int normalizedMaxFilesPerResult() {
        return maxFilesPerResult > 0 ? maxFilesPerResult : DEFAULT_MAX_FILES_PER_RESULT;
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#createDirectories] failed. path=" + path,
                    "산출물 저장 디렉터리를 생성하지 못했습니다.");
        }
    }

    private WriteSnapshot createSnapshot(Path target) {
        try {
            if (Files.exists(target)) {
                return new WriteSnapshot(target, true, Files.readString(target, StandardCharsets.UTF_8));
            }
            return new WriteSnapshot(target, false, "");
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#createSnapshot] failed. path=" + target,
                    "기존 산출물 파일 상태를 확인하지 못했습니다.");
        }
    }

    private void restoreSnapshots(List<WriteSnapshot> snapshots) {
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            restoreSnapshot(snapshots.get(index));
        }
    }

    private void restoreSnapshot(WriteSnapshot snapshot) {
        try {
            if (snapshot.existed()) {
                Files.writeString(
                        snapshot.target(),
                        snapshot.content(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                return;
            }
            Files.deleteIfExists(snapshot.target());
        } catch (IOException ignored) {
            // Result recording must continue with the original storage failure.
        }
    }

    private void writeFile(Path target, String content) {
        try {
            Files.writeString(
                    target,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#writeFile] failed. path=" + target,
                    "산출물 파일 저장에 실패했습니다.");
        }
    }

    private long fileSize(Path target) {
        try {
            return Files.size(target);
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#fileSize] failed. path=" + target,
                    "산출물 파일 크기를 조회하지 못했습니다.");
        }
    }

    private String readString(Path target) {
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#readString] failed. path=" + target,
                    "산출물 파일 내용을 조회하지 못했습니다.");
        }
    }

    private ServiceException invalidPath(String path) {
        return new ServiceException(
                CommonErrorCode.BAD_REQUEST,
                "[WorkspaceArtifactStorage#normalizeRelativePath] invalid path. path=" + path,
                "산출물 파일 경로가 올바르지 않습니다.");
    }

    private void requireWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId must be positive");
        }
    }

    private String toPortablePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String fileNameOf(Path path) {
        return Objects.requireNonNull(path.getFileName(), "file name must not be null")
                .toString();
    }

    private record ArtifactPath(Path relativePath, Path target) {}

    private record FileWritePlan(Path relativePath, Path target, String content, long sizeBytes) {}

    private record WriteSnapshot(Path target, boolean existed, String content) {}
}
