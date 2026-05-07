package back.domain.github.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GithubCredentialCreateReq(
        @NotBlank(message = "자격 증명 이름(Display Name)은 필수입니다.")
        String displayName,

        @NotBlank(message = "GitHub Token(PAT) 원문은 필수입니다.")
        String token
) {
}