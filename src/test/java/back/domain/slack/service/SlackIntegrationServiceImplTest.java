package back.domain.slack.service;

import back.domain.slack.client.SlackClient;
import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.dto.response.SlackOAuthAccessRes;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.domain.workspace.entity.WorkspaceMember;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * {@link SlackIntegrationServiceImpl}의 비즈니스 로직을 검증하는 테스트 클래스입니다.
 * OAuth 자동 연동 및 수동 등록/수정/삭제 로직을 포함합니다.
 */
@ExtendWith(MockitoExtension.class)
class SlackIntegrationServiceImplTest {

    @Mock
    private SlackIntegrationRepository slackIntegrationRepository;

    @Mock
    private WorkspaceAccessValidator workspaceAccessValidator;

    @Mock
    private SlackClient slackClient;

    @InjectMocks
    private SlackIntegrationServiceImpl slackIntegrationService;

    private WorkspaceMember workspaceMember;

    @BeforeEach
    void setUp() {
        workspaceMember = Mockito.mock(WorkspaceMember.class);
    }

    @Test
    @DisplayName("OAuth 설치 URL을 생성할 때 state 값에 workspaceId와 memberId가 인코딩되어 포함된다.")
    void getOAuthInstallUrl_success() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        String payload = workspaceId + ":" + memberId;
        String expectedState = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);

        // when
        String url = slackIntegrationService.getOAuthInstallUrl(workspaceId, memberId);

        // then
        assertThat(url).contains("https://slack.com/oauth/v2/authorize");
        assertThat(url).contains("state=" + expectedState);
        assertThat(url).contains("scope=chat:write,incoming-webhook");
    }

    @Test
    @DisplayName("OAuth 콜백 처리 시 코드를 토큰으로 교환하고 중복이 아니면 DB에 저장한다.")
    void handleOAuthCallback_newIntegration_success() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        String code = "test-code";
        String payload = workspaceId + ":" + memberId;
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        SlackOAuthAccessRes.Team team = new SlackOAuthAccessRes.Team("T12345", "Test Team");
        SlackOAuthAccessRes.IncomingWebhook webhook = new SlackOAuthAccessRes.IncomingWebhook("C12345", "https://url");
        SlackOAuthAccessRes oauthRes = new SlackOAuthAccessRes(true, "xoxb-token", team, webhook, null);

        given(slackClient.exchangeToken(eq(code), any(), any(), any())).willReturn(oauthRes);
        given(slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId("T12345", "C12345"))
                .willReturn(Optional.empty()); // 신규

        // when
        slackIntegrationService.handleOAuthCallback(code, state);

        // then
        verify(slackIntegrationRepository).save(any(SlackIntegration.class));
    }

    @Test
    @DisplayName("OAuth 콜백 처리 시 이미 연동된 채널이면 토큰만 갱신한다.")
    void handleOAuthCallback_existingIntegration_updatesToken() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        String code = "test-code";
        String payload = workspaceId + ":" + memberId;
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        SlackOAuthAccessRes.Team team = new SlackOAuthAccessRes.Team("T12345", "Test Team");
        SlackOAuthAccessRes.IncomingWebhook webhook = new SlackOAuthAccessRes.IncomingWebhook("C12345", "https://url");
        SlackOAuthAccessRes oauthRes = new SlackOAuthAccessRes(true, "xoxb-newtoken", team, webhook, null);

        SlackIntegration existing = SlackIntegration.builder()
                .workspaceId(workspaceId).slackTeamId("T12345").slackChannelId("C12345")
                .botToken("xoxb-oldtoken").createdByMemberId(memberId).build();

        given(slackClient.exchangeToken(eq(code), any(), any(), any())).willReturn(oauthRes);
        given(slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId("T12345", "C12345"))
                .willReturn(Optional.of(existing)); // 기존 존재

        // when
        slackIntegrationService.handleOAuthCallback(code, state);

        // then
        assertThat(existing.getBotToken()).isEqualTo("xoxb-newtoken");
        verify(slackIntegrationRepository, Mockito.never()).save(any()); // save 호출 안 됨
    }

    @Test
    @DisplayName("중복되지 않은 유효한 정보가 주어지면, 성공적으로 연동 정보가 등록되고 마스킹된 토큰이 반환된다.")
    void createSlackIntegration_success() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T12345", "C12345", "xoxb-real-bot-token-for-test"
        );

        SlackIntegration mockEntity = SlackIntegration.builder()
                .workspaceId(workspaceId)
                .slackTeamId(req.slackTeamId())
                .slackChannelId(req.slackChannelId())
                .botToken(req.botToken())
                .createdByMemberId(memberId)
                .build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.existsBySlackTeamIdAndSlackChannelId(req.slackTeamId(), req.slackChannelId()))
                .willReturn(false);
        given(slackIntegrationRepository.save(any(SlackIntegration.class)))
                .willReturn(mockEntity);

        // when
        SlackIntegrationInfoRes res = slackIntegrationService.createSlackIntegration(workspaceId, memberId, req);

        // then
        assertThat(res).isNotNull();
        assertThat(res.slackTeamId()).isEqualTo("T12345");
        assertThat(res.maskedBotToken()).isEqualTo("xoxb-****test");
        verify(workspaceAccessValidator).requireAdmin(workspaceId, memberId);
        verify(slackIntegrationRepository).save(any(SlackIntegration.class));
    }

    @Test
    @DisplayName("관리자 권한이 없는 사용자가 요청 시 ServiceException(FORBIDDEN)이 발생한다.")
    void createSlackIntegration_notAdmin_throwsException() {
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq("T12345", "C12345", "xoxb-token");

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId))
                .willThrow(new ServiceException(CommonErrorCode.FORBIDDEN, "not admin", "워크스페이스 관리자 권한이 필요합니다."));

        assertThatThrownBy(() -> slackIntegrationService.createSlackIntegration(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not admin");
    }

    @Test
    @DisplayName("이미 존재하는 슬랙 팀과 채널 정보를 등록하려고 하면 ServiceException(CONFLICT)이 발생한다.")
    void createSlackIntegration_duplicate_throws_exception() {
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq("T12345", "C12345", "botToken");

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.existsBySlackTeamIdAndSlackChannelId(req.slackTeamId(), req.slackChannelId()))
                .willReturn(true);

        assertThatThrownBy(() -> slackIntegrationService.createSlackIntegration(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Duplicate integration");
    }

    @Test
    @DisplayName("워크스페이스에 등록된 슬랙 연동 정보 목록을 조회한다.")
    void getSlackIntegrations_success() {
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegration integration = SlackIntegration.builder()
                .workspaceId(workspaceId).slackTeamId("T1").slackChannelId("C1")
                .botToken("xoxb-token12345").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireMember(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.findAllByWorkspaceId(workspaceId)).willReturn(List.of(integration));

        List<SlackIntegrationInfoRes> res = slackIntegrationService.getSlackIntegrations(workspaceId, memberId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).slackTeamId()).isEqualTo("T1");
    }


    @Test
    @DisplayName("수정할 필드만 입력된 경우 기존 값은 유지하고 전달된 값만 덮어쓴다.")
    void updateSlackIntegration_success() {
        Long workspaceId = 1L;
        Long integrationId = 10L;
        Long memberId = 100L;
        SlackIntegrationUpdateReq req = new SlackIntegrationUpdateReq(null, "C999", "xoxb-newtoken1234");

        SlackIntegration integration = SlackIntegration.builder()
                .workspaceId(workspaceId).slackTeamId("T1").slackChannelId("C1")
                .botToken("xoxb-oldtoken").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.findById(integrationId)).willReturn(Optional.of(integration));

        SlackIntegrationInfoRes res = slackIntegrationService.updateSlackIntegration(workspaceId, integrationId, memberId, req);

        assertThat(integration.getSlackChannelId()).isEqualTo("C999");
        assertThat(integration.getBotToken()).isEqualTo("xoxb-newtoken1234");
        assertThat(integration.getSlackTeamId()).isEqualTo("T1");
        assertThat(res.slackChannelId()).isEqualTo("C999");
    }

    @Test
    @DisplayName("다른 워크스페이스의 연동 정보를 수정하려고 하면 예외가 발생한다.")
    void updateSlackIntegration_workspaceMismatch_throwsException() {
        Long workspaceId = 1L;
        Long integrationId = 10L;
        Long memberId = 100L;
        SlackIntegrationUpdateReq req = new SlackIntegrationUpdateReq(null, "C999", null);

        SlackIntegration integration = SlackIntegration.builder()
                .workspaceId(999L).slackTeamId("T1").slackChannelId("C1")
                .botToken("token").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.findById(integrationId)).willReturn(Optional.of(integration));

        assertThatThrownBy(() -> slackIntegrationService.updateSlackIntegration(workspaceId, integrationId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Workspace mismatch");
    }

    @Test
    @DisplayName("연동 정보를 성공적으로 삭제한다.")
    void deleteSlackIntegration_success() {
        Long workspaceId = 1L;
        Long integrationId = 10L;
        Long memberId = 100L;
        SlackIntegration integration = SlackIntegration.builder().workspaceId(workspaceId).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.findById(integrationId)).willReturn(Optional.of(integration));

        slackIntegrationService.deleteSlackIntegration(workspaceId, integrationId, memberId);

        verify(slackIntegrationRepository).delete(integration);
    }
}