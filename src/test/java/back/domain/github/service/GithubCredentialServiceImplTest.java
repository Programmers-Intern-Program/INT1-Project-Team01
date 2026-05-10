package back.domain.github.service;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.request.GithubCredentialUpdateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.entity.GithubCredential;
import back.domain.github.repository.GithubCredentialRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.repository.WorkspaceRepository;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq("Main-Repo-Access", "ghp_realtoken1234567890");

        GithubCredential mockEntity = GithubCredential.builder()
                .workspace(workspace)
                .displayName(req.displayName())
                .token(req.token())
                .createdByMemberId(memberId)
                .build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));
        given(githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName())).willReturn(false);
        given(githubCredentialRepository.save(any(GithubCredential.class))).willReturn(mockEntity);

        GithubCredentialInfoRes res = githubCredentialService.createGithubCredential(workspaceId, memberId, req);

        assertThat(res).isNotNull();
        assertThat(res.displayName()).isEqualTo("Main-Repo-Access");
        assertThat(res.maskedToken()).isEqualTo("ghp_****7890");
    }

    @Test
    @DisplayName("동일한 워크스페이스 내에 중복된 displayName으로 등록 시도하면 ServiceException(CONFLICT)이 발생한다.")
    void createGithubCredential_duplicate_throws_exception() {
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq("Main-Repo-Access", "ghp_token123");

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));
        given(githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName())).willReturn(true);

        assertThatThrownBy(() -> githubCredentialService.createGithubCredential(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Duplicate displayName");
    }

    @Test
    @DisplayName("워크스페이스에 등록된 GitHub 자격 증명 목록을 조회한다.")
    void getGithubCredentials_success() {
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredential credential = GithubCredential.builder()
                .workspace(workspace).displayName("Repo1").token("ghp_token123").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireMember(workspaceId, memberId)).willReturn(workspaceMember);
        given(githubCredentialRepository.findAllByWorkspaceId(workspaceId)).willReturn(List.of(credential));

        List<GithubCredentialInfoRes> res = githubCredentialService.getGithubCredentials(workspaceId, memberId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).displayName()).isEqualTo("Repo1");
    }

    @Test
    @DisplayName("수정할 필드만 입력된 경우 기존 값은 유지하고 전달된 값만 덮어쓴다.")
    void updateGithubCredential_success() {
        Long workspaceId = 1L;
        Long credentialId = 20L;
        Long memberId = 100L;
        GithubCredentialUpdateReq req = new GithubCredentialUpdateReq(null, "ghp_newtoken0987");

        given(workspace.getId()).willReturn(workspaceId);

        GithubCredential credential = GithubCredential.builder()
                .workspace(workspace).displayName("Old-Name").token("ghp_oldtoken1234").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(githubCredentialRepository.findById(credentialId)).willReturn(Optional.of(credential));

        GithubCredentialInfoRes res = githubCredentialService.updateGithubCredential(workspaceId, credentialId, memberId, req);

        assertThat(credential.getToken()).isEqualTo("ghp_newtoken0987");
        assertThat(credential.getDisplayName()).isEqualTo("Old-Name");
    }

    @Test
    @DisplayName("다른 워크스페이스의 자격 증명을 수정하려고 하면 예외가 발생한다.")
    void updateGithubCredential_workspaceMismatch_throwsException() {
        Long workspaceId = 1L;
        Long credentialId = 20L;
        Long memberId = 100L;
        GithubCredentialUpdateReq req = new GithubCredentialUpdateReq("New Name", null);

        Workspace otherWorkspace = Mockito.mock(Workspace.class);
        given(otherWorkspace.getId()).willReturn(999L); // 다른 워크스페이스

        GithubCredential credential = GithubCredential.builder()
                .workspace(otherWorkspace).displayName("Old-Name").token("token").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(githubCredentialRepository.findById(credentialId)).willReturn(Optional.of(credential));

        assertThatThrownBy(() -> githubCredentialService.updateGithubCredential(workspaceId, credentialId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Workspace mismatch");
    }

    @Test
    @DisplayName("자격 증명 정보를 성공적으로 삭제한다.")
    void deleteGithubCredential_success() {
        Long workspaceId = 1L;
        Long credentialId = 20L;
        Long memberId = 100L;

        given(workspace.getId()).willReturn(workspaceId);
        GithubCredential credential = GithubCredential.builder().workspace(workspace).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(githubCredentialRepository.findById(credentialId)).willReturn(Optional.of(credential));

        githubCredentialService.deleteGithubCredential(workspaceId, credentialId, memberId);

        verify(githubCredentialRepository).delete(credential);
    }
}