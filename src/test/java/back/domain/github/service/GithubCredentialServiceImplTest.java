package back.domain.github.service;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.entity.GithubCredential;
import back.domain.github.repository.GithubCredentialRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.repository.WorkspaceRepository;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GithubCredentialServiceImplTest {

    @Mock
    private GithubCredentialRepository githubCredentialRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceAccessValidator workspaceAccessValidator;

    @InjectMocks
    private GithubCredentialServiceImpl githubCredentialService;

    private Workspace workspace;
    private WorkspaceMember workspaceMember;

    @BeforeEach
    void setUp() {
        workspace = Mockito.mock(Workspace.class);
        workspaceMember = Mockito.mock(WorkspaceMember.class);
    }

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
                .workspace(workspace)
                .displayName(req.displayName())
                .token(req.token())
                .createdByMemberId(memberId)
                .build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));
        given(githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName()))
                .willReturn(false);
        given(githubCredentialRepository.save(any(GithubCredential.class)))
                .willReturn(mockEntity);

        // when
        GithubCredentialInfoRes res = githubCredentialService.createGithubCredential(workspaceId, memberId, req);

        // then
        assertThat(res).isNotNull();
        assertThat(res.displayName()).isEqualTo("Main-Repo-Access");
        assertThat(res.maskedToken()).isEqualTo("ghp_****7890");
        verify(workspaceAccessValidator).requireAdmin(workspaceId, memberId);
        verify(githubCredentialRepository).save(any(GithubCredential.class));
    }

    @Test
    @DisplayName("관리자 권한이 없는 사용자가 요청 시 ServiceException(FORBIDDEN)이 발생한다.")
    void createGithubCredential_notAdmin_throwsException() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "Main-Repo-Access", "ghp_token123"
        );

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId))
                .willThrow(new ServiceException(CommonErrorCode.FORBIDDEN, "not admin", "워크스페이스 관리자 권한이 필요합니다."));

        // when & then
        assertThatThrownBy(() -> githubCredentialService.createGithubCredential(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not admin");
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스 ID로 요청 시 ServiceException(NOT_FOUND)이 발생한다.")
    void createGithubCredential_workspaceNotFound_throwsException() {
        // given
        Long workspaceId = 999L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "Main-Repo-Access", "ghp_token123"
        );

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId))
                .willThrow(new ServiceException(CommonErrorCode.NOT_FOUND, "workspace not found", "워크스페이스가 존재하지 않습니다."));

        // when & then
        assertThatThrownBy(() -> githubCredentialService.createGithubCredential(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("workspace not found");
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

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));
        given(githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> githubCredentialService.createGithubCredential(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Duplicate displayName");
    }
}