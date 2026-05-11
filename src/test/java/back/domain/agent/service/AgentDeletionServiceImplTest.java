package back.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.member.entity.Member;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class AgentDeletionServiceImplTest {

    @Mock
    private WorkspaceAccessValidator workspaceAccessValidator;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private TransactionOperations transactionOperations;

    @Mock
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @Mock
    private OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Mock
    private OpenClawGatewayClient openClawGatewayClient;

    @InjectMocks
    private AgentDeletionServiceImpl agentDeletionService;

    private Member member;
    private Workspace workspace;
    private WorkspaceMember admin;
    private OpenClawGatewayConnectionContext gatewayContext;

    @BeforeEach
    void setUp() {
        member = Member.createUser("sub", "test@test.com", "관리자");
        ReflectionTestUtils.setField(member, "id", 10L);

        workspace = Workspace.create("AI Office", "설명", member);
        ReflectionTestUtils.setField(workspace, "id", 1L);

        admin = WorkspaceMember.create(workspace, member, WorkspaceMemberRole.ADMIN);
        gatewayContext = new OpenClawGatewayConnectionContext("ws://localhost:34115", "gateway-secret-token");
        given(transactionOperations.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("OpenClaw Agent id가 있으면 Gateway Agent 삭제 후 Agent를 DISABLED 처리한다")
    void deleteAgent_readyAgent_success() {
        // given
        Agent agent = createAgent(100L, "Backend Agent");
        agent.markOpenClawCreated("openclaw-agent-1");
        agent.markReady();
        given(workspaceAccessValidator.requireAdmin(1L, 10L)).willReturn(admin);
        given(agentRepository.findByIdAndWorkspaceIdAndStatusNot(100L, 1L, AgentStatus.DISABLED))
                .willReturn(Optional.of(agent));
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);

        // when
        agentDeletionService.deleteAgent(1L, 10L, 100L);

        // then
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(agent.getSyncError()).isNull();
        verify(openClawGatewayClient).connect(gatewayContext);
        verify(openClawGatewayClient).deleteAgent("openclaw-agent-1", true);
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("OpenClaw Agent id가 없으면 Gateway 호출 없이 Agent를 DISABLED 처리한다")
    void deleteAgent_withoutOpenClawAgentId_disablesLocalAgent() {
        // given
        Agent agent = createAgent(100L, "Backend Agent");
        given(workspaceAccessValidator.requireAdmin(1L, 10L)).willReturn(admin);
        given(agentRepository.findByIdAndWorkspaceIdAndStatusNot(100L, 1L, AgentStatus.DISABLED))
                .willReturn(Optional.of(agent));

        // when
        agentDeletionService.deleteAgent(1L, 10L, 100L);

        // then
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        verify(openClawGatewayClientFactory, never()).create();
    }

    @Test
    @DisplayName("Agent가 없거나 이미 삭제된 상태이면 ServiceException이 발생한다")
    void deleteAgent_missingAgent_throwsException() {
        // given
        given(workspaceAccessValidator.requireAdmin(1L, 10L)).willReturn(admin);
        given(agentRepository.findByIdAndWorkspaceIdAndStatusNot(999L, 1L, AgentStatus.DISABLED))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agentDeletionService.deleteAgent(1L, 10L, 999L))
                .isInstanceOf(ServiceException.class)
                .extracting("clientMessage")
                .isEqualTo("Agent를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("Gateway 삭제 실패 시 Agent를 DISABLED 처리하지 않고 예외를 전파한다")
    void deleteAgent_gatewayFailure_keepsAgentActive() {
        // given
        Agent agent = createAgent(100L, "Backend Agent");
        agent.markOpenClawCreated("openclaw-agent-1");
        agent.markReady();
        given(workspaceAccessValidator.requireAdmin(1L, 10L)).willReturn(admin);
        given(agentRepository.findByIdAndWorkspaceIdAndStatusNot(100L, 1L, AgentStatus.DISABLED))
                .willReturn(Optional.of(agent));
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        willThrow(OpenClawGatewayException.rpcTimeout("agents.delete", "req-1"))
                .given(openClawGatewayClient)
                .deleteAgent("openclaw-agent-1", true);

        // when & then
        assertThatThrownBy(() -> agentDeletionService.deleteAgent(1L, 10L, 100L))
                .isInstanceOf(OpenClawGatewayException.class);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.READY);
        verify(openClawGatewayClient).close();
    }

    private Agent createAgent(Long id, String name) {
        Agent agent = Agent.create(workspace, name, AgentCategory.BACKEND, "~/.openclaw/workspace-1", 10L);
        ReflectionTestUtils.setField(agent, "id", id);
        return agent;
    }
}
