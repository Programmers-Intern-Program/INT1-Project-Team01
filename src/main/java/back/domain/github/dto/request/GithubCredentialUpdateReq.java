package back.domain.github.dto.request;

/**
 * GitHub 자격 증명(PAT)의 부분 수정을 위한 요청 DTO입니다.
 * 수정이 필요한 필드만 선택적으로 포함하며, 포함되지 않은 필드는 기존 값이 유지됩니다.
 */
public record GithubCredentialUpdateReq(
        String displayName,
        String token
) {
}