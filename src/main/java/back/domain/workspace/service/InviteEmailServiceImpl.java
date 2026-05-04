package back.domain.workspace.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import back.domain.workspace.email.InviteEmailCommand;
import back.domain.workspace.entity.WorkspaceInvite;
import back.domain.workspace.repository.WorkspaceInviteRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음")
@Service
public class InviteEmailServiceImpl implements InviteEmailService {
    private static final Logger log = LoggerFactory.getLogger(InviteEmailServiceImpl.class);
    private static final DateTimeFormatter EXPIRES_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String EMAIL_TEMPLATE_PATH = "templates/invite-email.html";

    private final JavaMailSender mailSender;
    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final String from;
    private final String fromName;
    private final String subjectPrefix;

    public InviteEmailServiceImpl(
            JavaMailSender mailSender,
            WorkspaceInviteRepository workspaceInviteRepository,
            @Value("${custom.invite.email.from:}") String configuredFrom,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${custom.invite.email.from-name:AI Office}") String fromName,
            @Value("${custom.invite.email.subject-prefix:[AI Office]}") String subjectPrefix) {
        this.mailSender = mailSender;
        this.workspaceInviteRepository = workspaceInviteRepository;
        this.from = resolveFrom(configuredFrom, mailUsername);
        this.fromName = resolveFromName(fromName);
        this.subjectPrefix = subjectPrefix;
    }

    @Override
    @Async("emailTaskExecutor") // 비동기 처리
    @Transactional
    public void sendAsync(InviteEmailCommand command) {
        WorkspaceInvite workspaceInvite =
                workspaceInviteRepository.findById(command.inviteId()).orElse(null);
        // ====== 실패 처리 ======
        // 1. workspaceInvite 자체가 없을 때
        if (workspaceInvite == null) {
            log.warn(
                    "[InviteEmailServiceImpl#sendAsync] invite email skipped because invite is missing. inviteId={}, targetEmail={}",
                    command.inviteId(),
                    command.targetEmail());
            return;
        }

        // 2. targetEmail이 없을 때
        if (command.targetEmail() == null || command.targetEmail().isBlank()) {
            workspaceInvite.markEmailFailed("target email is blank");
            return;
        }

        workspaceInvite.markEmailSending();
        // 3. from(mail 발송자)이 없을 때
        if (from.isBlank()) {
            workspaceInvite.markEmailFailed("sender address is blank");
            log.warn(
                    "[InviteEmailServiceImpl#sendAsync] invite email skipped because sender address is blank. workspaceId={}, inviteId={}, targetEmail={}",
                    command.workspaceId(),
                    command.inviteId(),
                    command.targetEmail());
            return;
        }

        // Email 본문 생성
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(command.targetEmail().trim());
            helper.setSubject("%s %s Workspace 초대".formatted(subjectPrefix, command.workspaceName()));
            helper.setText(buildHtmlBody(command), true);
            mailSender.send(message);
            workspaceInvite.markEmailSent();
            log.info(
                    "[InviteEmailServiceImpl#sendAsync] invite email sent. workspaceId={}, inviteId={}, targetEmail={}",
                    command.workspaceId(),
                    command.inviteId(),
                    command.targetEmail());
        } catch (MailException ex) {
            log.warn(
                    "[InviteEmailServiceImpl#sendAsync] invite email failed. workspaceId={}, inviteId={}, targetEmail={}, reason={}",
                    command.workspaceId(),
                    command.inviteId(),
                    command.targetEmail(),
                    ex.getMessage());
            workspaceInvite.markEmailFailed(ex.getMessage());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.warn(
                    "[InviteEmailServiceImpl#sendAsync] invite email message creation failed. workspaceId={}, inviteId={}, targetEmail={}, reason={}",
                    command.workspaceId(),
                    command.inviteId(),
                    command.targetEmail(),
                    ex.getMessage());
            workspaceInvite.markEmailFailed(ex.getMessage());
        } catch (ServiceException ex) {
            workspaceInvite.markEmailFailed(ex.getMessage());
            log.warn(
                    "[InviteEmailServiceImpl#sendAsync] invite email template failed. workspaceId={}, inviteId={}, targetEmail={}, reason={}",
                    command.workspaceId(),
                    command.inviteId(),
                    command.targetEmail(),
                    ex.getMessage());
        }
    }

    // Email body 생성 (html)
    private String buildHtmlBody(InviteEmailCommand command) {
        try {
            String template = new ClassPathResource(EMAIL_TEMPLATE_PATH).getContentAsString(StandardCharsets.UTF_8);
            return template.replace("{{inviterName}}", html(command.inviterName()))
                    .replace("{{workspaceName}}", html(command.workspaceName()))
                    .replace("{{inviteUrl}}", html(command.inviteUrl()))
                    .replace("{{role}}", html(command.role().name()))
                    .replace("{{expiresAt}}", html(command.expiresAt().format(EXPIRES_AT_FORMATTER)));
        } catch (IOException e) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[InviteEmailServiceImpl#buildHtmlBody] fail to read email template file. path="
                            + EMAIL_TEMPLATE_PATH,
                    "이메일 템플릿 파일을 읽는 데 실패했습니다.");
        }
    }

    private String resolveFrom(String configuredFrom, String mailUsername) {
        if (configuredFrom != null && !configuredFrom.isBlank()) {
            return configuredFrom.trim();
        }
        if (mailUsername != null && !mailUsername.isBlank()) {
            return mailUsername.trim();
        }
        return "";
    }

    private String resolveFromName(String fromName) {
        if (fromName == null || fromName.isBlank()) {
            return "AI Office";
        }
        return fromName.trim();
    }

    private String html(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
