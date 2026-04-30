package back.domain.workspace.service;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    @Mock private MemberRepository memberRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;

    @InjectMocks
    private WorkspaceServiceImpl workspaceService;

    private Member member;
    private Workspace workspace;
    private WorkspaceMember adminWorkspaceMember;

    @BeforeEach
    void setUp() {
        member = Member.createUser("sub", "test@test.com", "홍길동");
        ReflectionTestUtils.setField(member, "id", 1L);

        workspace = Workspace.create("테스트 워크스페이스", "설명", member);
        ReflectionTestUtils.setField(workspace, "id", 1L);

        adminWorkspaceMember = WorkspaceMember.create(workspace, member, WorkspaceMemberRole.ADMIN);
    }

    @Test
    @DisplayName("워크스페이스 생성 성공")
    void create_success() {
        // given
        CreateWorkspaceReq request = new CreateWorkspaceReq("새 워크스페이스", "설명");

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceRepository.save(any(Workspace.class))).willReturn(workspace);
        given(workspaceMemberRepository.save(any(WorkspaceMember.class))).willReturn(adminWorkspaceMember);

        // when
        WorkspaceInfoRes result = workspaceService.create(1L, request);

        // then
        assertThat(result.name()).isEqualTo("테스트 워크스페이스");
        assertThat(result.myRole()).isEqualTo(WorkspaceMemberRole.ADMIN);
    }

    @Test
    @DisplayName("워크스페이스 생성 실패 - 회원 없음")
    void create_memberNotFound_throwsException() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> workspaceService.create(1L, new CreateWorkspaceReq("이름", null)))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("내 워크스페이스 목록 조회 성공")
    void listMyWorkspaces_success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceMemberRepository.findAllByMemberId(1L)).willReturn(List.of(adminWorkspaceMember));

        // when
        List<WorkspaceSummaryInfoRes> result = workspaceService.listMyWorkspaces(1L);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("워크스페이스 상세 조회 성공")
    void getWorkspace_success() {
        // given
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));

        // when
        WorkspaceInfoRes result = workspaceService.getWorkspace(1L, 1L);

        // then
        assertThat(result.workspaceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("워크스페이스 상세 조회 실패 - 워크스페이스 없음")
    void getWorkspace_workspaceNotFound_throwsException() {
        // given
        given(workspaceRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> workspaceService.getWorkspace(1L, 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("워크스페이스 상세 조회 실패 - 멤버 아님")
    void getWorkspace_notMember_throwsException() {
        // given
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> workspaceService.getWorkspace(1L, 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("워크스페이스 수정 성공")
    void updateWorkspace_success() {
        // given
        UpdateWorkspaceReq request = new UpdateWorkspaceReq("수정된 이름", "수정된 설명");

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));

        // when
        WorkspaceInfoRes result = workspaceService.updateWorkspace(1L, 1L, request);

        // then
        assertThat(result.name()).isEqualTo("수정된 이름");
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 관리자 아님")
    void updateWorkspace_notAdmin_throwsException() {
        // given
        WorkspaceMember memberRole = WorkspaceMember.create(workspace, member, WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).willReturn(Optional.of(memberRole));

        // when & then
        assertThatThrownBy(() -> workspaceService.updateWorkspace(1L, 1L, new UpdateWorkspaceReq("이름", null)))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("워크스페이스 멤버 목록 조회 성공")
    void listMembers_success() {
        // given
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceMemberRepository.findAllByWorkspaceId(1L)).willReturn(List.of(adminWorkspaceMember));

        // when
        List<WorkspaceMemberInfoRes> result = workspaceService.listMembers(1L, 1L);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("멤버 역할 변경 성공")
    void changeMemberRole_success() {
        // given
        Member targetMember = Member.createUser("sub2", "target@test.com", "대상유저");
        ReflectionTestUtils.setField(targetMember, "id", 2L);
        WorkspaceMember targetWorkspaceMember = WorkspaceMember.create(workspace, targetMember, WorkspaceMemberRole.MEMBER);
        UpdateWorkspaceRoleReq request = new UpdateWorkspaceRoleReq(WorkspaceMemberRole.ADMIN);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L))
                .willReturn(Optional.of(targetWorkspaceMember));

        // when & then
        assertThatCode(() -> workspaceService.changeMemberRole(1L, 2L, request, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("멤버 역할 변경 실패 - 마지막 관리자 변경 불가")
    void changeMemberRole_lastAdmin_throwsException() {
        // given
        UpdateWorkspaceRoleReq request = new UpdateWorkspaceRoleReq(WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceMemberRepository.countByWorkspaceIdAndRole(1L, WorkspaceMemberRole.ADMIN)).willReturn(1L);

        // when & then
        assertThatThrownBy(() -> workspaceService.changeMemberRole(1L, 1L, request, 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("멤버 삭제 성공")
    void removeMember_success() {
        // given
        Member targetMember = Member.createUser("sub2", "target@test.com", "대상유저");
        ReflectionTestUtils.setField(targetMember, "id", 2L);
        WorkspaceMember targetWorkspaceMember = WorkspaceMember.create(workspace, targetMember, WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L))
                .willReturn(Optional.of(targetWorkspaceMember));

        // when
        workspaceService.removeMember(1L, 2L, 1L);

        // then
        verify(workspaceMemberRepository).delete(targetWorkspaceMember);
    }

    @Test
    @DisplayName("멤버 삭제 실패 - 마지막 관리자 삭제 불가")
    void removeMember_lastAdmin_throwsException() {
        // given
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceMemberRepository.countByWorkspaceIdAndRole(1L, WorkspaceMemberRole.ADMIN)).willReturn(1L);

        // when & then
        assertThatThrownBy(() -> workspaceService.removeMember(1L, 1L, 1L))
                .isInstanceOf(ServiceException.class);
    }
}
