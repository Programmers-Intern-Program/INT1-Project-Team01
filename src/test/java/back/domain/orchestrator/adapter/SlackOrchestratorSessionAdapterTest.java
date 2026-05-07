package back.domain.orchestrator.adapter;

import back.domain.orchestrator.dto.request.OrchestratorSessionCreateCommand;
import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.service.OrchestratorSessionService;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.domain.workspace.entity.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackOrchestratorSessionAdapterTest {

    @InjectMocks
    private SlackOrchestratorSessionAdapter adapter;

    @Mock
    private OrchestratorSessionService orchestratorSessionService;

    @Mock
    private SlackIntegrationRepository slackIntegrationRepository;

    @Test
    @DisplayName("DB에 Integration 정보가 있으면 Command 객체를 올바르게 조립하여 서비스를 호출한다.")
    void createSession_Success() {
        // given
        String teamId = "T123";
        String channelId = "C123";
        String targetTs = "1000.000";
        String text = "테스트 메시지";

        Workspace mockWorkspace = mock(Workspace.class);
        when(mockWorkspace.getId()).thenReturn(1L);

        SlackIntegration mockIntegration = mock(SlackIntegration.class);
        when(mockIntegration.getWorkspace()).thenReturn(mockWorkspace);

        when(slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId(teamId, channelId))
                .thenReturn(Optional.of(mockIntegration));

        // when
        adapter.createSession(teamId, channelId, targetTs, text);

        // then
        ArgumentCaptor<OrchestratorSessionCreateCommand> captor = ArgumentCaptor.forClass(OrchestratorSessionCreateCommand.class);
        verify(orchestratorSessionService).createSession(captor.capture());

        OrchestratorSessionCreateCommand command = captor.getValue();
        assertThat(command.workspaceId()).isEqualTo(1L);
        assertThat(command.source()).isEqualTo(OrchestratorSessionSource.SLACK);
        assertThat(command.sourceRef()).isEqualTo("T123:C123:1000.000");
        assertThat(command.userMessage()).isEqualTo("테스트 메시지");
        assertThat(command.requestedByMemberId()).isNull();
    }

    @Test
    @DisplayName("매핑된 Integration 정보가 없으면 IllegalStateException을 발생시킨다.")
    void createSession_ThrowsException_WhenIntegrationNotFound() {
        // given
        when(slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adapter.createSession("T123", "C123", "1000", "text"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace 정보를 찾을 수 없습니다");
    }
}