package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SlackIntegrationServiceImplTest {

    @Mock
    private SlackIntegrationRepository slackIntegrationRepository;

    @Mock
    private WorkspaceAccessValidator workspaceAccessValidator;

    @InjectMocks
    private SlackIntegrationServiceImpl slackIntegrationService;

    private WorkspaceMember workspaceMember;

    @BeforeEach
    void setUp() {
        workspaceMember = Mockito.mock(WorkspaceMember.class);
    }

    @Test
    @DisplayName("중복되지 않은 유효한 정보가 주어지면, 성공적으로 연동 정보가 등록되고 마스킹된 토큰이 반환된다.")
    void createSlackIntegration_success() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T12345", "C12345", "xoxb-real-bot-token-for-test", "secret-key"
        );

        SlackIntegration mockEntity = SlackIntegration.builder()
                .workspaceId(workspaceId)
                .slackTeamId(req.slackTeamId())
                .slackChannelId(req.slackChannelId())
                .botToken(req.botToken())
                .signingSecret(req.signingSecret())
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
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq("T12345", "C12345", "xoxb-token", "secret");

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
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq("T12345", "C12345", "botToken", "secret");

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
                .botToken("xoxb-token12345").signingSecret("sec").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
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
        SlackIntegrationUpdateReq req = new SlackIntegrationUpdateReq(null, "C999", "xoxb-newtoken1234", null);

        SlackIntegration integration = SlackIntegration.builder()
                .workspaceId(workspaceId).slackTeamId("T1").slackChannelId("C1")
                .botToken("xoxb-oldtoken").signingSecret("old-secret").createdByMemberId(memberId).build();

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.findById(integrationId)).willReturn(Optional.of(integration));

        SlackIntegrationInfoRes res = slackIntegrationService.updateSlackIntegration(workspaceId, integrationId, memberId, req);

        assertThat(integration.getSlackChannelId()).isEqualTo("C999");
        assertThat(integration.getBotToken()).isEqualTo("xoxb-newtoken1234");
        assertThat(integration.getSlackTeamId()).isEqualTo("T1");
        assertThat(integration.getSigningSecret()).isEqualTo("old-secret");
        assertThat(res.slackChannelId()).isEqualTo("C999");
    }

    @Test
    @DisplayName("다른 워크스페이스의 연동 정보를 수정하려고 하면 예외가 발생한다.")
    void updateSlackIntegration_workspaceMismatch_throwsException() {
        Long workspaceId = 1L;
        Long integrationId = 10L;
        Long memberId = 100L;
        SlackIntegrationUpdateReq req = new SlackIntegrationUpdateReq(null, "C999", null, null);

        SlackIntegration integration = SlackIntegration.builder()
                .workspaceId(999L).slackTeamId("T1").slackChannelId("C1")
                .botToken("token").signingSecret("sec").createdByMemberId(memberId).build();

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