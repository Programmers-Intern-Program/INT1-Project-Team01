package back.domain.orchestrator.adapter;

import back.domain.orchestrator.dto.request.OrchestratorSessionCreateCommand;
import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.service.OrchestratorSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SlackOrchestratorSessionAdapterTest {

    @InjectMocks
    private SlackOrchestratorSessionAdapter adapter;

    @Mock
    private OrchestratorSessionService orchestratorSessionService;

    @Test
    @DisplayName("workspaceId와 text를 전달받아 Command 객체를 올바르게 조립하여 서비스를 호출한다.")
    void createSession_Success() {
        // given
        Long workspaceId = 1L;
        String sourceRef = "T123:C123:1000.000";
        String targetTs = "1000.000";
        String text = "테스트 메시지";

        // when
        adapter.createSession(workspaceId, sourceRef, targetTs, text);

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
}