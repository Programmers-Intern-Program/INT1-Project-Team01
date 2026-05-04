package back.domain.github.service;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.entity.GithubCredential;
import back.domain.github.repository.GithubCredentialRepository;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GithubCredentialServiceImplTest {

    @Mock
    private GithubCredentialRepository githubCredentialRepository;

    @InjectMocks
    private GithubCredentialServiceImpl githubCredentialService;

    // TODO: Workspace 검증 로직 호출 (ADMIN인지 확인) [IT-9]

    @Test
    @DisplayName("중복되지 않은 유효한 정보가 주어지면, 성공적으로 PAT가 등록되고 마스킹된 토큰이 반환된다.")
    void createGithubCredential_success() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "Main-Repo-Access", "ghp_realtoken1234567890"
        );

        GithubCredential mockEntity = GithubCredential.builder()
                .workspaceId(workspaceId)
                .displayName(req.displayName())
                .token(req.token())
                .createdByMemberId(memberId)
                .build();

        given(githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName()))
                .willReturn(false);
        given(githubCredentialRepository.save(any(GithubCredential.class)))
                .willReturn(mockEntity);

        // when
        GithubCredentialInfoRes res = githubCredentialService.createGithubCredential(workspaceId, memberId, req);

        // then
        assertThat(res).isNotNull();
        assertThat(res.displayName()).isEqualTo("Main-Repo-Access");
        // 응답 컨벤션(ghp_****...) 검증
        assertThat(res.maskedToken()).isEqualTo("ghp_****7890");

        verify(githubCredentialRepository).save(any(GithubCredential.class));
    }

    @Test
    @DisplayName("동일한 워크스페이스 내에 중복된 displayName으로 등록 시도하면 ServiceException(CONFLICT)이 발생한다.")
    void createGithubCredential_duplicate_throws_exception() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "Main-Repo-Access", "ghp_token123"
        );

        given(githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> githubCredentialService.createGithubCredential(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Duplicate displayName");
    }
}