package back.domain.artifact.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkspaceArtifactStorage {

    private static final String DEFAULT_BASE_PATH = "runtime-workspaces";
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_FILES_PER_RESULT = 50;
    private static final int DEFAULT_MAX_TREE_DEPTH = 8;
    private static final int DEFAULT_MAX_TREE_NODES = 500;
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

    @Value("${app.artifact.max-tree-depth:8}")
    private int maxTreeDepth;

    @Value("${app.artifact.max-tree-nodes:500}")
    private int maxTreeNodes;

    public List<StoredArtifactFile> storeFiles(Long workspaceId, List<ArtifactFileSaveCommand> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        requireWorkspaceId(workspaceId);
        validateFileCount(files);
        Path projectRoot = resolveProjectRoot(workspaceId);
        List<FileWritePlan> plans =
                files.stream().map(file -> toFileWritePlan(projectRoot, file)).toList();
        createDirectories(projectRoot, projectRoot.toString());
        return writeFiles(plans);
    }

    public List<StoredArtifactFile> storeFilesFromWorkspace(
            Long workspaceId, Path sourceRoot, List<ArtifactFileSaveCommand> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        requireWorkspaceId(workspaceId);
        validateFileCount(files);
        Path projectRoot = resolveProjectRoot(workspaceId);
        Path normalizedSourceRoot = normalizeWorkspaceRoot(sourceRoot);
        validateReadableSourceRoot(normalizedSourceRoot);
        List<FileWritePlan> plans = files.stream()
                .map(file -> toWorkspaceFileWritePlan(projectRoot, normalizedSourceRoot, file))
                .toList();
        createDirectories(projectRoot, projectRoot.toString());
        return writeFiles(plans);
    }

    public List<StoredArtifactFile> storeAvailableFilesFromWorkspace(
            Long workspaceId, Path sourceRoot, List<ArtifactFileSaveCommand> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        requireWorkspaceId(workspaceId);
        Path normalizedSourceRoot = normalizeWorkspaceRoot(sourceRoot);
        validateReadableSourceRoot(normalizedSourceRoot);
        List<StoredArtifactFile> storedFiles = new ArrayList<>();
        for (ArtifactFileSaveCommand file : uniqueFilesWithinLimit(files)) {
            try {
                storedFiles.addAll(storeFilesFromWorkspace(workspaceId, normalizedSourceRoot, List.of(file)));
            } catch (RuntimeException exception) {
                log.warn(
                        "Skipping unavailable workspace artifact file. workspaceId={}, path={}",
                        workspaceId,
                        file.path(),
                        exception);
            }
        }
        return List.copyOf(storedFiles);
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
        if (!Files.exists(projectRoot, LinkOption.NOFOLLOW_LINKS)) {
            return new ArtifactTree(List.of());
        }
        validateNoSymlinkSegments(projectRoot, projectRoot.toString());
        if (!Files.isDirectory(projectRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#listProjectTree] project root is not a directory. path=" + projectRoot,
                    "산출물 루트 디렉터리가 올바르지 않습니다.");
        }
        AtomicInteger nodeCount = new AtomicInteger();
        return new ArtifactTree(listChildren(
                projectRoot,
                projectRoot,
                1,
                normalizedMaxTreeDepth(),
                normalizedMaxTreeNodes(),
                nodeCount));
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
        validateExtension(relativePath);
        Path target = resolveWithinProjectRoot(projectRoot, relativePath, path);
        String portablePath = toPortablePath(relativePath);
        String fileName = fileNameOf(relativePath);
        String contentType = resolveContentType(relativePath);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return new ArtifactFileReference(portablePath, fileName, contentType, null, false);
        }
        validateReadableFile(projectRoot, relativePath, target, path);
        return new ArtifactFileReference(portablePath, fileName, contentType, fileSize(target), true);
    }

    private FileWritePlan toFileWritePlan(Path projectRoot, ArtifactFileSaveCommand file) {
        Path relativePath = normalizeRelativePath(file.path());
        validateExtension(relativePath);
        int sizeBytes = file.content().getBytes(StandardCharsets.UTF_8).length;
        validateFileSize(relativePath, sizeBytes);
        Path target = resolveWithinProjectRoot(projectRoot, relativePath, file.path());
        return new FileWritePlan(relativePath, target, file.content(), sizeBytes, file.path());
    }

    private FileWritePlan toWorkspaceFileWritePlan(
            Path projectRoot, Path sourceRoot, ArtifactFileSaveCommand file) {
        Path relativePath = normalizeRelativePath(file.path());
        validateExtension(relativePath);
        Path source = resolveWithinRoot(sourceRoot, relativePath, file.path());
        validateReadableSourceFile(sourceRoot, relativePath, source, file.path());
        long sizeBytes = fileSize(source);
        validateFileSize(relativePath, sizeBytes);
        Path target = resolveWithinProjectRoot(projectRoot, relativePath, file.path());
        return new FileWritePlan(relativePath, target, readString(source), sizeBytes, file.path());
    }

    private List<StoredArtifactFile> writeFiles(List<FileWritePlan> plans) {
        List<WriteSnapshot> snapshots = new ArrayList<>();
        try {
            for (FileWritePlan plan : plans) {
                createDirectories(
                        Objects.requireNonNull(plan.target().getParent(), "target parent must not be null"),
                        plan.originalPath());
                validateWritableTarget(plan);
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
        return resolveWithinRoot(projectRoot, relativePath, originalPath);
    }

    private Path resolveWithinRoot(Path root, Path relativePath, String originalPath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw invalidPath(originalPath);
        }
        return target;
    }

    private ArtifactPath resolveExistingFile(Path projectRoot, String path) {
        Path relativePath = normalizeRelativePath(path);
        validateExtension(relativePath);
        Path target = resolveWithinProjectRoot(projectRoot, relativePath, path);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "[WorkspaceArtifactStorage#resolveExistingFile] file not found. path=" + relativePath,
                    "산출물 파일을 찾을 수 없습니다.");
        }
        validateReadableFile(projectRoot, relativePath, target, path);
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

    private void validateReadableFile(Path projectRoot, Path relativePath, Path target, String originalPath) {
        validateNoSymlinkSegments(target, originalPath);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw invalidPath(originalPath);
        }
        validateExtension(relativePath);
        validateRealPath(projectRoot, target, originalPath);
    }

    private void validateReadableSourceRoot(Path sourceRoot) {
        validateNoSymlinkSegmentsWithin(sourceRoot, sourceRoot, sourceRoot.toString());
        if (!Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "[WorkspaceArtifactStorage#validateReadableSourceRoot] source root not found. path="
                            + sourceRoot,
                    "Agent 작업 디렉터리를 찾을 수 없습니다.");
        }
        if (!Files.isDirectory(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw invalidPath(sourceRoot.toString());
        }
    }

    private void validateReadableSourceFile(
            Path sourceRoot, Path relativePath, Path target, String originalPath) {
        validateNoSymlinkSegmentsWithin(sourceRoot, target, originalPath);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "[WorkspaceArtifactStorage#validateReadableSourceFile] source file not found. path=" + target,
                    "Agent 작업 파일을 찾을 수 없습니다.");
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw invalidPath(originalPath);
        }
        validateExtension(relativePath);
        validateRealPath(sourceRoot, target, originalPath);
    }

    private void validateWritableTarget(FileWritePlan plan) {
        validateNoSymlinkSegments(plan.target(), plan.originalPath());
        if (Files.exists(plan.target(), LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(plan.target(), LinkOption.NOFOLLOW_LINKS)) {
            throw invalidPath(plan.originalPath());
        }
    }

    private void validateNoSymlinkSegments(Path target, String originalPath) {
        validateNoSymlinkSegmentsWithin(normalizedBasePath(), target, originalPath);
    }

    private void validateNoSymlinkSegmentsWithin(Path base, Path target, String originalPath) {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedBase)) {
            throw invalidPath(originalPath);
        }
        rejectIfSymlink(normalizedBase, originalPath);
        Path current = normalizedBase;
        for (Path segment : normalizedBase.relativize(normalizedTarget)) {
            current = current.resolve(segment);
            rejectIfSymlink(current, originalPath);
        }
    }

    private void rejectIfSymlink(Path path, String originalPath) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw invalidPath(originalPath);
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

    private List<ArtifactTreeNode> listChildren(
            Path projectRoot,
            Path directory,
            int depth,
            int maxDepth,
            int maxNodes,
            AtomicInteger nodeCount) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> shouldIncludeTreePath(projectRoot, path))
                    .sorted(this::compareTreePath)
                    .map(path -> toTreeNode(projectRoot, path, depth, maxDepth, maxNodes, nodeCount))
                    .toList();
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#listChildren] failed. path=" + directory,
                    "산출물 파일 트리를 조회하지 못했습니다.");
        }
    }

    private boolean shouldIncludeTreePath(Path projectRoot, Path path) {
        if (Files.isSymbolicLink(path)) {
            return false;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        Path relativePath = projectRoot.relativize(path);
        return !isBlockedExtension(relativePath);
    }

    private int compareTreePath(Path left, Path right) {
        boolean leftDirectory = Files.isDirectory(left, LinkOption.NOFOLLOW_LINKS);
        boolean rightDirectory = Files.isDirectory(right, LinkOption.NOFOLLOW_LINKS);
        if (leftDirectory != rightDirectory) {
            return leftDirectory ? -1 : 1;
        }
        return Comparator.comparing(this::fileNameOf, String.CASE_INSENSITIVE_ORDER)
                .compare(left, right);
    }

    private ArtifactTreeNode toTreeNode(
            Path projectRoot,
            Path path,
            int depth,
            int maxDepth,
            int maxNodes,
            AtomicInteger nodeCount) {
        registerTreeNode(path, maxNodes, nodeCount);
        Path relativePath = projectRoot.relativize(path);
        String portablePath = toPortablePath(relativePath);
        String name = fileNameOf(path);
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            List<ArtifactTreeNode> children = depth >= maxDepth
                    ? List.of()
                    : listChildren(projectRoot, path, depth + 1, maxDepth, maxNodes, nodeCount);
            return new ArtifactTreeNode(
                    name, portablePath, ArtifactTreeNodeType.DIRECTORY, null, null, children);
        }
        return new ArtifactTreeNode(
                name,
                portablePath,
                ArtifactTreeNodeType.FILE,
                resolveContentType(relativePath),
                fileSize(path),
                List.of());
    }

    private void registerTreeNode(Path path, int maxNodes, AtomicInteger nodeCount) {
        int currentCount = nodeCount.incrementAndGet();
        if (currentCount > maxNodes) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[WorkspaceArtifactStorage#registerTreeNode] tree node limit exceeded. path="
                            + path
                            + ", limit="
                            + maxNodes,
                    "산출물 파일 트리 크기가 허용 범위를 초과했습니다.");
        }
    }

    private String resolveContentType(Path relativePath) {
        String fileName = fileNameOf(relativePath).toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.entrySet().stream()
                .filter(entry -> fileName.endsWith(entry.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse("text/plain");
    }

    private boolean isBlockedExtension(Path relativePath) {
        String fileName = fileNameOf(relativePath).toLowerCase(Locale.ROOT);
        return BLOCKED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
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

    private List<ArtifactFileSaveCommand> uniqueFilesWithinLimit(List<ArtifactFileSaveCommand> files) {
        Map<String, ArtifactFileSaveCommand> uniqueFiles = new LinkedHashMap<>();
        int limit = normalizedMaxFilesPerResult();
        for (ArtifactFileSaveCommand file : files) {
            if (file == null || uniqueFiles.containsKey(file.path())) {
                continue;
            }
            if (uniqueFiles.size() >= limit) {
                break;
            }
            uniqueFiles.put(file.path(), file);
        }
        return List.copyOf(uniqueFiles.values());
    }

    private Path normalizedBasePath() {
        String value = basePath == null || basePath.isBlank() ? DEFAULT_BASE_PATH : basePath.trim();
        return Path.of(value).toAbsolutePath().normalize();
    }

    private Path normalizeWorkspaceRoot(Path sourceRoot) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot must not be null");
        }
        String value = sourceRoot.toString();
        if ("~".equals(value) || value.startsWith("~/")) {
            value = System.getProperty("user.home") + value.substring(1);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private long normalizedMaxFileSizeBytes() {
        return maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    private int normalizedMaxFilesPerResult() {
        return maxFilesPerResult > 0 ? maxFilesPerResult : DEFAULT_MAX_FILES_PER_RESULT;
    }

    private int normalizedMaxTreeDepth() {
        return maxTreeDepth > 0 ? maxTreeDepth : DEFAULT_MAX_TREE_DEPTH;
    }

    private int normalizedMaxTreeNodes() {
        return maxTreeNodes > 0 ? maxTreeNodes : DEFAULT_MAX_TREE_NODES;
    }

    private void createDirectories(Path path, String originalPath) {
        try {
            validateNoSymlinkSegments(path, originalPath);
            Files.createDirectories(path);
            validateNoSymlinkSegments(path, originalPath);
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceArtifactStorage#createDirectories] failed. path=" + path,
                    "산출물 저장 디렉터리를 생성하지 못했습니다.");
        }
    }

    private WriteSnapshot createSnapshot(Path target) {
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
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
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
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
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
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

    private record FileWritePlan(Path relativePath, Path target, String content, long sizeBytes, String originalPath) {}

    private record WriteSnapshot(Path target, boolean existed, String content) {}
}
