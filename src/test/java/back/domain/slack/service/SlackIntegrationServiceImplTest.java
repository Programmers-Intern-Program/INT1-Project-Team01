package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
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
class SlackIntegrationServiceImplTest {

    @Mock
    private SlackIntegrationRepository slackIntegrationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private SlackIntegrationServiceImpl slackIntegrationService;

    // TODO: Workspace 검증 로직 호출 (ADMIN인지 확인) [IT-9]

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = Mockito.mock(Workspace.class);
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
                .workspace(workspace)
                .slackTeamId(req.slackTeamId())
                .slackChannelId(req.slackChannelId())
                .botToken(req.botToken())
                .signingSecret(req.signingSecret())
                .createdByMemberId(memberId)
                .build();

        given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));
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
        verify(slackIntegrationRepository).save(any(SlackIntegration.class));
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

        given(workspaceRepository.findById(workspaceId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> slackIntegrationService.createSlackIntegration(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class);
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

        given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));
        given(slackIntegrationRepository.existsBySlackTeamIdAndSlackChannelId(req.slackTeamId(), req.slackChannelId()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> slackIntegrationService.createSlackIntegration(workspaceId, memberId, req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Duplicate integration");
    }
}