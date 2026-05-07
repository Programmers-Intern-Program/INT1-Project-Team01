package back.domain.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.member.entity.Member;
import back.domain.workspace.email.InviteEmailCommand;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceInvite;
import back.domain.workspace.enums.InviteEmailStatus;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceInviteRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class InviteEmailServiceImplTest {

    @Test
    @DisplayName("초대 이메일 발송 성공")
    void sendAsync_success() throws Exception {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WorkspaceInviteRepository workspaceInviteRepository = mock(WorkspaceInviteRepository.class);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        WorkspaceInvite workspaceInvite = createInvite();
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(workspaceInviteRepository.findById(10L)).willReturn(Optional.of(workspaceInvite));
        InviteEmailServiceImpl service = new InviteEmailServiceImpl(
                mailSender, workspaceInviteRepository, "noreply@test.com", "", "AI Office", "[AI Office]");

        // when
        service.sendAsync(createCommand("invitee@test.com"));

        // then
        verify(mailSender).send(mimeMessage);
        assertThat(workspaceInvite.getEmailStatus()).isEqualTo(InviteEmailStatus.SENT);
        assertThat(workspaceInvite.getEmailSentAt()).isNotNull();
    }

    @Test
    @DisplayName("초대 대상 이메일이 없으면 발송하지 않고 FAILED 상태로 변경")
    void sendAsync_blankTargetEmail_doesNotSend() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WorkspaceInviteRepository workspaceInviteRepository = mock(WorkspaceInviteRepository.class);
        WorkspaceInvite workspaceInvite = createInvite();
        given(workspaceInviteRepository.findById(10L)).willReturn(Optional.of(workspaceInvite));
        InviteEmailServiceImpl service = new InviteEmailServiceImpl(
                mailSender, workspaceInviteRepository, "noreply@test.com", "", "AI Office", "[AI Office]");

        // when
        service.sendAsync(createCommand(" "));

        // then
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(workspaceInvite.getEmailStatus()).isEqualTo(InviteEmailStatus.FAILED);
    }

    @Test
    @DisplayName("invite가 존재하지 않으면 발송하지 않음")
    void sendAsync_inviteNotFound_doesNotSend() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WorkspaceInviteRepository workspaceInviteRepository = mock(WorkspaceInviteRepository.class);
        given(workspaceInviteRepository.findById(10L)).willReturn(Optional.empty());
        InviteEmailServiceImpl service = new InviteEmailServiceImpl(
                mailSender, workspaceInviteRepository, "noreply@test.com", "", "AI Office", "[AI Office]");

        // when
        service.sendAsync(createCommand("invitee@test.com"));

        // then
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("custom from이 없으면 spring.mail.username을 발신자로 사용")
    void sendAsync_emptyConfiguredFrom_usesMailUsername() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WorkspaceInviteRepository workspaceInviteRepository = mock(WorkspaceInviteRepository.class);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        WorkspaceInvite workspaceInvite = createInvite();
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(workspaceInviteRepository.findById(10L)).willReturn(Optional.of(workspaceInvite));
        InviteEmailServiceImpl service = new InviteEmailServiceImpl(
                mailSender, workspaceInviteRepository, "", "mail-user@test.com", "AI Office", "[AI Office]");

        // when
        service.sendAsync(createCommand("invitee@test.com"));

        // then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("발신자 주소가 없으면 발송하지 않고 FAILED 상태로 변경")
    void sendAsync_blankFromAddress_marksEmailFailed() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WorkspaceInviteRepository workspaceInviteRepository = mock(WorkspaceInviteRepository.class);
        WorkspaceInvite workspaceInvite = createInvite();
        given(workspaceInviteRepository.findById(10L)).willReturn(Optional.of(workspaceInvite));
        InviteEmailServiceImpl service =
                new InviteEmailServiceImpl(mailSender, workspaceInviteRepository, "", "", "AI Office", "[AI Office]");

        // when
        service.sendAsync(createCommand("invitee@test.com"));

        // then
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(workspaceInvite.getEmailStatus()).isEqualTo(InviteEmailStatus.FAILED);
    }

    @Test
    @DisplayName("이메일 발송 실패 시 FAILED 상태로 변경")
    void sendAsync_sendFail_marksEmailFailed() {
        // given
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WorkspaceInviteRepository workspaceInviteRepository = mock(WorkspaceInviteRepository.class);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        WorkspaceInvite workspaceInvite = createInvite();
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(workspaceInviteRepository.findById(10L)).willReturn(Optional.of(workspaceInvite));
        willThrow(new MailSendException("smtp failed")).given(mailSender).send(mimeMessage);
        InviteEmailServiceImpl service = new InviteEmailServiceImpl(
                mailSender, workspaceInviteRepository, "noreply@test.com", "", "AI Office", "[AI Office]");

        // when
        service.sendAsync(createCommand("invitee@test.com"));

        // then
        assertThat(workspaceInvite.getEmailStatus()).isEqualTo(InviteEmailStatus.FAILED);
    }

    private InviteEmailCommand createCommand(String targetEmail) {
        return new InviteEmailCommand(
                1L,
                "테스트 워크스페이스",
                10L,
                "http://localhost:8080/api/v1/invites/token/accept",
                WorkspaceMemberRole.MEMBER,
                LocalDateTime.of(2026, 5, 11, 0, 0),
                1L,
                "홍길동",
                targetEmail);
    }

    private WorkspaceInvite createInvite() {
        Member member = Member.createUser("sub", "test@test.com", "홍길동");
        ReflectionTestUtils.setField(member, "id", 1L);
        Workspace workspace = Workspace.create("테스트 워크스페이스", "설명", member);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        WorkspaceInvite invite = WorkspaceInvite.create(
                workspace, "token", WorkspaceMemberRole.MEMBER, member, LocalDateTime.of(2026, 5, 11, 0, 0));
        ReflectionTestUtils.setField(invite, "id", 10L);
        invite.requestEmailDelivery("invitee@test.com");
        return invite;
    }
}
