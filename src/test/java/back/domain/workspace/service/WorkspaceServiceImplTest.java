package back.domain.workspace.service;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.workspace.dto.request.CreateWorkspaceInviteReq;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.ExtendWorkspaceInviteReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.response.WorkspaceInviteManagementRes;
import back.domain.workspace.dto.response.WorkspaceInviteInfoRes;
import back.domain.workspace.dto.response.WorkspaceInvitePreviewRes;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceInvite;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceInviteStatus;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceInviteRepository;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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
    @Mock private WorkspaceInviteRepository workspaceInviteRepository;
    @Mock private InviteEmailService inviteEmailService;

    private WorkspaceServiceImpl workspaceService;
    private WorkspaceAccessValidator workspaceAccessValidator;

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

        workspaceAccessValidator = new WorkspaceAccessValidator(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceServiceImpl(
                memberRepository,
                workspaceRepository,
                workspaceMemberRepository,
                workspaceInviteRepository,
                inviteEmailService,
                workspaceAccessValidator);
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
        given(workspaceMemberRepository.findAllByMemberIdWithWorkspace(1L)).willReturn(List.of(adminWorkspaceMember));

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

    @Test
    @DisplayName("초대 링크 생성 성공")
    void createInviteLink_success() {
        // given
        CreateWorkspaceInviteReq request = new CreateWorkspaceInviteReq(7, WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.existsByToken(any())).willReturn(false);
        given(workspaceInviteRepository.save(any(WorkspaceInvite.class))).willAnswer(invocation -> {
            WorkspaceInvite invite = invocation.getArgument(0);
            ReflectionTestUtils.setField(invite, "id", 1L);
            return invite;
        });

        // when
        WorkspaceInviteInfoRes result = workspaceService.createInviteLink(1L, 1L, request);

        // then
        assertThat(result.inviteId()).isEqualTo(1L);
        assertThat(result.token()).isNotBlank();
        assertThat(result.inviteUrl()).startsWith("http://localhost:8080/api/v1/invites/");
        verify(workspaceInviteRepository).save(any(WorkspaceInvite.class));
    }

    @Test
    @DisplayName("초대 링크 생성 실패 - 관리자 아님")
    void createInviteLink_notAdmin_throwsException() {
        // given
        WorkspaceMember memberRole = WorkspaceMember.create(workspace, member, WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).willReturn(Optional.of(memberRole));

        // when & then
        assertThatThrownBy(() -> workspaceService.createInviteLink(
                        1L, 1L, new CreateWorkspaceInviteReq(7, WorkspaceMemberRole.MEMBER)))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("내가 보낸 초대 목록 조회 성공 - status 없으면 PENDING 기본 조회")
    void listMySentInvites_success() {
        // given
        WorkspaceInvite pendingInvite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(pendingInvite, "id", 1L);
        WorkspaceInvite acceptedInvite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(acceptedInvite, "id", 2L);
        acceptedInvite.accept(member);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.findAllByWorkspaceIdAndCreatedByMemberIdOrderByIdDesc(1L, 1L))
                .willReturn(List.of(acceptedInvite, pendingInvite));

        // when
        List<WorkspaceInviteManagementRes> result = workspaceService.listMySentInvites(1L, 1L, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).inviteId()).isEqualTo(1L);
        assertThat(result.get(0).status()).isEqualTo(WorkspaceInviteStatus.PENDING);
    }

    @Test
    @DisplayName("내가 보낸 초대 목록 조회 성공 - PENDING 초대만 조회")
    void listMySentInvites_pendingStatus_filtersPendingOnly() {
        // given
        WorkspaceInvite pendingInvite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(pendingInvite, "id", 1L);
        WorkspaceInvite acceptedInvite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(acceptedInvite, "id", 2L);
        acceptedInvite.accept(member);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.findAllByWorkspaceIdAndCreatedByMemberIdOrderByIdDesc(1L, 1L))
                .willReturn(List.of(acceptedInvite, pendingInvite));

        // when
        List<WorkspaceInviteManagementRes> result =
                workspaceService.listMySentInvites(1L, 1L, "PENDING");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).inviteId()).isEqualTo(1L);
        assertThat(result.get(0).status()).isEqualTo(WorkspaceInviteStatus.PENDING);
    }

    @Test
    @DisplayName("내가 보낸 초대 목록 조회 성공 - ACCEPTED 초대만 조회")
    void listMySentInvites_acceptedStatus_filtersAcceptedOnly() {
        // given
        WorkspaceInvite pendingInvite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(pendingInvite, "id", 1L);
        WorkspaceInvite acceptedInvite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(acceptedInvite, "id", 2L);
        acceptedInvite.accept(member);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.findAllByWorkspaceIdAndCreatedByMemberIdOrderByIdDesc(1L, 1L))
                .willReturn(List.of(acceptedInvite, pendingInvite));

        // when
        List<WorkspaceInviteManagementRes> result =
                workspaceService.listMySentInvites(1L, 1L, " accepted ");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).inviteId()).isEqualTo(2L);
        assertThat(result.get(0).status()).isEqualTo(WorkspaceInviteStatus.ACCEPTED);
    }

    @Test
    @DisplayName("내가 보낸 초대 목록 조회 실패 - status가 enum에 없으면 예외")
    void listMySentInvites_invalidStatus_throwsException() {
        // given
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));

        // when & then
        assertThatThrownBy(() -> workspaceService.listMySentInvites(1L, 1L, "unknown"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("내가 보낸 초대 삭제 성공 - 초대 링크 폐기")
    void deleteInvite_success() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(invite, "id", 1L);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.findByIdAndWorkspaceIdAndCreatedByMemberId(1L, 1L, 1L))
                .willReturn(Optional.of(invite));

        // when
        workspaceService.deleteInvite(1L, 1L, 1L);

        // then
        assertThat(invite.getRevokedAt()).isNotNull();
        assertThat(invite.getStatus(LocalDateTime.now())).isEqualTo(WorkspaceInviteStatus.REVOKED);
    }

    @Test
    @DisplayName("내가 보낸 초대 삭제 실패 - 이미 수락된 초대")
    void deleteInvite_acceptedInvite_throwsException() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(invite, "id", 1L);
        invite.accept(member);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.findByIdAndWorkspaceIdAndCreatedByMemberId(1L, 1L, 1L))
                .willReturn(Optional.of(invite));

        // when & then
        assertThatThrownBy(() -> workspaceService.deleteInvite(1L, 1L, 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("내가 보낸 초대 연장 성공")
    void extendInvite_success() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(invite, "id", 1L);
        LocalDateTime beforeExpiresAt = invite.getExpiresAt();

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.findByIdAndWorkspaceIdAndCreatedByMemberId(1L, 1L, 1L))
                .willReturn(Optional.of(invite));

        // when
        WorkspaceInviteManagementRes result =
                workspaceService.extendInvite(1L, 1L, 1L, new ExtendWorkspaceInviteReq(3));

        // then
        assertThat(result.expiresAt()).isAfter(beforeExpiresAt);
        assertThat(invite.getExpiresAt()).isAfterOrEqualTo(beforeExpiresAt.plusDays(3));
    }

    @Test
    @DisplayName("초대 조회 성공")
    void getInviteInfo_success() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(invite, "id", 1L);
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));

        // when
        WorkspaceInvitePreviewRes result = workspaceService.getInviteInfo("token");

        // then
        assertThat(result.inviteId()).isEqualTo(1L);
        assertThat(result.workspaceName()).isEqualTo("테스트 워크스페이스");
        assertThat(result.role()).isEqualTo(WorkspaceMemberRole.MEMBER);
        assertThat(result.status()).isEqualTo(WorkspaceInviteStatus.PENDING);
        assertThat(result.expired()).isFalse();
    }

    @Test
    @DisplayName("초대 수락 성공 - targetEmail이 없으면 공개 링크로 여러 명 참여 가능")
    void acceptInvite_openInvite_doesNotConsumeInvite() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));
        given(workspaceMemberRepository.existsByWorkspaceIdAndMemberId(1L, 1L)).willReturn(false);

        // when
        workspaceService.acceptInvite("token", 1L);

        // then
        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
        assertThat(invite.getAcceptedAt()).isNull();
        assertThat(invite.getAcceptedByMember()).isNull();
        assertThat(invite.getStatus(LocalDateTime.now())).isEqualTo(WorkspaceInviteStatus.PENDING);
    }

    @Test
    @DisplayName("초대 수락 성공 - targetEmail이 있으면 해당 이메일 회원 1명만 참여 가능")
    void acceptInvite_targetEmailInvite_consumesInvite() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        invite.requestEmailDelivery("test@test.com");

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));
        given(workspaceMemberRepository.existsByWorkspaceIdAndMemberId(1L, 1L)).willReturn(false);

        // when
        workspaceService.acceptInvite("token", 1L);

        // then
        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
        assertThat(invite.getAcceptedAt()).isNotNull();
        assertThat(invite.getAcceptedByMember()).isEqualTo(member);
        assertThat(invite.getStatus(LocalDateTime.now())).isEqualTo(WorkspaceInviteStatus.ACCEPTED);
    }

    @Test
    @DisplayName("초대 수락 실패 - targetEmail 초대는 다른 이메일 회원이 수락할 수 없음")
    void acceptInvite_targetEmailMismatch_throwsException() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        invite.requestEmailDelivery("invitee@test.com");

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));

        // when & then
        assertThatThrownBy(() -> workspaceService.acceptInvite("token", 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 수락 실패 - 만료된 초대")
    void acceptInvite_expiredInvite_throwsException() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().minusDays(1));

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));

        // when & then
        assertThatThrownBy(() -> workspaceService.acceptInvite("token", 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 수락 실패 - 이미 멤버")
    void acceptInvite_alreadyMember_throwsException() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));
        given(workspaceMemberRepository.existsByWorkspaceIdAndMemberId(1L, 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> workspaceService.acceptInvite("token", 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("내 워크스페이스 목록 조회 실패 - 회원 없음")
    void listMyWorkspaces_memberNotFound_throwsException() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> workspaceService.listMyWorkspaces(1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 조회 실패 - 빈 토큰")
    void getInviteInfo_blankToken_throwsException() {
        // when & then
        assertThatThrownBy(() -> workspaceService.getInviteInfo("  "))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 조회 실패 - null 토큰")
    void getInviteInfo_nullToken_throwsException() {
        // when & then
        assertThatThrownBy(() -> workspaceService.getInviteInfo(null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 수락 실패 - 이미 수락된 초대")
    void acceptInvite_alreadyAccepted_throwsException() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        invite.accept(member);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));

        // when & then
        assertThatThrownBy(() -> workspaceService.acceptInvite("token", 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 수락 실패 - 폐기된 초대")
    void acceptInvite_revokedInvite_throwsException() {
        // given
        WorkspaceInvite invite = createInvite(LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(invite, "revokedAt", LocalDateTime.now());

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(workspaceInviteRepository.findByToken("token")).willReturn(Optional.of(invite));

        // when & then
        assertThatThrownBy(() -> workspaceService.acceptInvite("token", 1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 링크 생성 성공 - role null이면 MEMBER 기본값 사용")
    void createInviteLink_nullRole_defaultsToMember() {
        // given
        CreateWorkspaceInviteReq request = new CreateWorkspaceInviteReq(7, null);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.existsByToken(any())).willReturn(false);
        given(workspaceInviteRepository.save(any(WorkspaceInvite.class))).willAnswer(invocation -> {
            WorkspaceInvite invite = invocation.getArgument(0);
            ReflectionTestUtils.setField(invite, "id", 1L);
            return invite;
        });

        // when
        WorkspaceInviteInfoRes result = workspaceService.createInviteLink(1L, 1L, request);

        // then
        assertThat(result.inviteId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("초대 링크 생성 성공 - expiresInDays null이면 7일 기본값 사용")
    void createInviteLink_nullExpiresInDays_defaultsToSevenDays() {
        // given
        CreateWorkspaceInviteReq request = new CreateWorkspaceInviteReq(null, WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.existsByToken(any())).willReturn(false);
        given(workspaceInviteRepository.save(any(WorkspaceInvite.class))).willAnswer(invocation -> {
            WorkspaceInvite invite = invocation.getArgument(0);
            ReflectionTestUtils.setField(invite, "id", 2L);
            return invite;
        });

        // when
        WorkspaceInviteInfoRes result = workspaceService.createInviteLink(1L, 1L, request);

        // then
        assertThat(result.expiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    @DisplayName("초대 링크 생성 실패 - 토큰 생성 5회 모두 중복")
    void createInviteLink_tokenGenerationAllFail_throwsException() {
        // given
        CreateWorkspaceInviteReq request = new CreateWorkspaceInviteReq(7, WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.existsByToken(any())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> workspaceService.createInviteLink(1L, 1L, request))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("초대 링크 생성 성공 - base URL 끝에 슬래시가 있어도 정상 처리")
    void createInviteLink_baseUrlWithTrailingSlash_buildsCorrectUrl() {
        // given
        ReflectionTestUtils.setField(workspaceService, "inviteBaseUrl", "http://localhost:8080/api/v1/invites/");
        CreateWorkspaceInviteReq request = new CreateWorkspaceInviteReq(7, WorkspaceMemberRole.MEMBER);

        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(adminWorkspaceMember));
        given(workspaceInviteRepository.existsByToken(any())).willReturn(false);
        given(workspaceInviteRepository.save(any(WorkspaceInvite.class))).willAnswer(invocation -> {
            WorkspaceInvite invite = invocation.getArgument(0);
            ReflectionTestUtils.setField(invite, "id", 3L);
            return invite;
        });

        // when
        WorkspaceInviteInfoRes result = workspaceService.createInviteLink(1L, 1L, request);

        // then
        assertThat(result.inviteUrl()).doesNotContain("//accept");
    }

    private WorkspaceInvite createInvite(LocalDateTime expiresAt) {
        return WorkspaceInvite.create(workspace, "token", WorkspaceMemberRole.MEMBER, member, expiresAt);
    }
}
