package back.domain.artifact.service;

import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.artifact.dto.StoredArtifactFile;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceArtifactStorage {

    private static final String DEFAULT_BASE_PATH = "runtime-workspaces";
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_FILES_PER_RESULT = 50;
    private static final Set<String> BLOCKED_EXTENSIONS =
            Set.of(".class", ".jar", ".war", ".zip", ".exe", ".dll", ".dylib", ".so");

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
        List<FileWritePlan> plans = files.stream()
                .map(file -> toFileWritePlan(projectRoot, file))
                .toList();
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

    private FileWritePlan toFileWritePlan(Path projectRoot, ArtifactFileSaveCommand file) {
        Path relativePath = normalizeRelativePath(file.path());
        validateExtension(relativePath);
        int sizeBytes = file.content().getBytes(StandardCharsets.UTF_8).length;
        validateFileSize(relativePath, sizeBytes);
        Path target = projectRoot.resolve(relativePath).normalize();
        if (!target.startsWith(projectRoot)) {
            throw invalidPath(file.path());
        }
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

    private boolean startsWithParentReference(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
    }

    private void validateExtension(Path relativePath) {
        Path fileNamePath = Objects.requireNonNull(relativePath.getFileName(), "file name must not be null");
        String fileName = fileNamePath.toString().toLowerCase(Locale.ROOT);
        boolean blocked = BLOCKED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
        if (blocked) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[WorkspaceArtifactStorage#validateExtension] blocked file extension. path=" + relativePath,
                    "저장할 수 없는 파일 형식입니다.");
        }
    }

    private void validateFileSize(Path relativePath, int sizeBytes) {
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

    private record FileWritePlan(Path relativePath, Path target, String content, long sizeBytes) {}

    private record WriteSnapshot(Path target, boolean existed, String content) {}
}
