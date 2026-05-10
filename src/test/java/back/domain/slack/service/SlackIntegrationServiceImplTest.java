package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
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
    private WorkspaceAccessValidator workspaceAccessValidator; // workspaceRepository mock 삭제됨

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
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T12345", "C12345", "xoxb-token", "secret"
        );

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId))
                .willThrow(new ServiceException(CommonErrorCode.FORBIDDEN, "not admin", "워크스페이스 관리자 권한이 필요합니다."));

        // when & then
        assertThatThrownBy(() -> slackIntegrationService.createSlackIntegration(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not admin");
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스 ID로 요청 시 ServiceException(NOT_FOUND)이 발생한다.")
    void createSlackIntegration_workspaceNotFound_throwsException() {
        // given
        Long workspaceId = 999L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T12345", "C12345", "xoxb-token", "secret"
        );

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId))
                .willThrow(new ServiceException(CommonErrorCode.NOT_FOUND, "workspace not found", "워크스페이스가 존재하지 않습니다."));

        // when & then
        assertThatThrownBy(() -> slackIntegrationService.createSlackIntegration(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("workspace not found");
    }

    @Test
    @DisplayName("이미 존재하는 슬랙 팀과 채널 정보를 등록하려고 하면 ServiceException(CONFLICT)이 발생한다.")
    void createSlackIntegration_duplicate_throws_exception() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T12345", "C12345", "botToken", "secret"
        );

        given(workspaceAccessValidator.requireAdmin(workspaceId, memberId)).willReturn(workspaceMember);
        given(slackIntegrationRepository.existsBySlackTeamIdAndSlackChannelId(req.slackTeamId(), req.slackChannelId()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> slackIntegrationService.createSlackIntegration(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Duplicate integration");
    }
}