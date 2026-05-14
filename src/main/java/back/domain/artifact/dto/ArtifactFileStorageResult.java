package back.domain.artifact.dto;

import java.util.List;

public record ArtifactFileStorageResult(List<StoredArtifactFile> storedFiles, int requestedFileCount) {

    private static final String ALL_FILES_MISSING_WARNING =
            "보고된 파일 산출물을 실제 Agent 작업 디렉터리에서 찾지 못해 저장하지 못했습니다.";
    private static final String PARTIAL_FILES_MISSING_WARNING =
            "일부 파일 산출물을 실제 Agent 작업 디렉터리에서 찾지 못해 저장하지 못했습니다.";

    public ArtifactFileStorageResult {
        storedFiles = storedFiles == null ? List.of() : List.copyOf(storedFiles);
        if (requestedFileCount < 0) {
            throw new IllegalArgumentException("requestedFileCount must not be negative");
        }
    }

    public String warningMessage() {
        if (storedFiles.size() == requestedFileCount) {
            return null;
        }
        if (storedFiles.isEmpty()) {
            return ALL_FILES_MISSING_WARNING;
        }
        return PARTIAL_FILES_MISSING_WARNING;
    }
}
