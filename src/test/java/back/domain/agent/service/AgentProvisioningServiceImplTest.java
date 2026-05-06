package back.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import back.domain.agent.dto.request.AgentSkillFileReq;
import back.domain.agent.dto.request.OpenClawAgentCreateReq;
import back.domain.agent.dto.response.OpenClawAgentCreateRes;
import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentSkillFile;
import back.domain.agent.entity.AgentSkillSyncStatus;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.agent.repository.AgentSkillFileRepository;
import back.domain.gateway.client.OpenClawAgentCreateCommand;
import back.domain.gateway.client.OpenClawAgentFileCommand;
import back.domain.gateway.client.OpenClawAgentSummary;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.member.entity.Member;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class AgentProvisioningServiceImplTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentSkillFileRepository agentSkillFileRepository;

    @Mock
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @Mock
    private OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Mock
    private OpenClawGatewayClient openClawGatewayClient;

    @Mock
    private TransactionOperations transactionOperations;

    @InjectMocks
    private AgentProvisioningServiceImpl agentProvisioningService;

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
    @DisplayName("Agent 생성 성공 시 OpenClaw agent를 만들고 Skill file을 동기화한 뒤 READY로 응답한다")
    void createAgent_success_marksReady() {
        // given
        OpenClawAgentCreateReq request = new OpenClawAgentCreateReq(
                "Backend Agent", null, "tool", List.of(new AgentSkillFileReq("AGENTS.md", "You are a backend agent.")));
        givenWorkspaceAdmin();
        given(agentRepository.existsByWorkspaceIdAndName(1L, "Backend Agent")).willReturn(false);
        given(agentRepository.save(any(Agent.class))).willAnswer(agentSaveAnswer(100L));
        given(agentSkillFileRepository.save(any(AgentSkillFile.class))).willAnswer(skillFileSaveAnswer());
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.createAgent(any(OpenClawAgentCreateCommand.class)))
                .willReturn(new OpenClawAgentSummary("openclaw-agent-1", "workspace-1-agent-100"));

        // when
        OpenClawAgentCreateRes response = agentProvisioningService.createAgent(1L, 10L, request);

        // then
        assertThat(response.status()).isEqualTo(AgentStatus.READY);
        assertThat(response.openClawAgentId()).isEqualTo("openclaw-agent-1");
        assertThat(response.workspacePath()).isEqualTo("~/.openclaw/workspace-1");
        assertThat(response.skillFiles()).hasSize(1);
        assertThat(response.skillFiles().getFirst().syncStatus()).isEqualTo(AgentSkillSyncStatus.SYNCED);

        ArgumentCaptor<OpenClawAgentCreateCommand> createCommandCaptor =
                ArgumentCaptor.forClass(OpenClawAgentCreateCommand.class);
        verify(openClawGatewayClient).connect(gatewayContext);
        verify(openClawGatewayClient).createAgent(createCommandCaptor.capture());
        assertThat(createCommandCaptor.getValue().name()).isEqualTo("workspace-1-agent-100");
        assertThat(createCommandCaptor.getValue().workspace()).isEqualTo("~/.openclaw/workspace-1");
        assertThat(createCommandCaptor.getValue().emoji()).isEqualTo("tool");

        ArgumentCaptor<OpenClawAgentFileCommand> fileCommandCaptor =
                ArgumentCaptor.forClass(OpenClawAgentFileCommand.class);
        verify(openClawGatewayClient).setAgentFile(fileCommandCaptor.capture());
        assertThat(fileCommandCaptor.getValue().agentId()).isEqualTo("openclaw-agent-1");
        assertThat(fileCommandCaptor.getValue().name()).isEqualTo("AGENTS.md");
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Gateway binding이 없으면 Agent를 ERROR로 남기고 OpenClaw를 호출하지 않는다")
    void createAgent_missingGatewayBinding_marksError() {
        // given
        OpenClawAgentCreateReq request = new OpenClawAgentCreateReq("Backend Agent", null, null, List.of());
        givenWorkspaceAdmin();
        given(agentRepository.existsByWorkspaceIdAndName(1L, "Backend Agent")).willReturn(false);
        given(agentRepository.save(any(Agent.class))).willAnswer(agentSaveAnswer(100L));
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willThrow(new ServiceException(
                        CommonErrorCode.NOT_FOUND, "gateway binding not found", "워크스페이스 Gateway 설정이 존재하지 않습니다."));

        // when
        OpenClawAgentCreateRes response = agentProvisioningService.createAgent(1L, 10L, request);

        // then
        assertThat(response.status()).isEqualTo(AgentStatus.ERROR);
        assertThat(response.syncError()).isEqualTo("워크스페이스 Gateway 설정이 존재하지 않습니다.");
        verify(openClawGatewayClientFactory, never()).create();
    }

    @Test
    @DisplayName("OpenClaw agents.create 실패 시 Agent를 ERROR로 남기고 Skill sync를 시도하지 않는다")
    void createAgent_gatewayCreateFailed_marksError() {
        // given
        OpenClawAgentCreateReq request = new OpenClawAgentCreateReq(
                "Backend Agent", null, null, List.of(new AgentSkillFileReq("AGENTS.md", "You are a backend agent.")));
        givenWorkspaceAdmin();
        given(agentRepository.existsByWorkspaceIdAndName(1L, "Backend Agent")).willReturn(false);
        given(agentRepository.save(any(Agent.class))).willAnswer(agentSaveAnswer(100L));
        given(agentSkillFileRepository.save(any(AgentSkillFile.class))).willAnswer(skillFileSaveAnswer());
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.createAgent(any(OpenClawAgentCreateCommand.class)))
                .willThrow(OpenClawGatewayException.connectionFailed(new IllegalStateException("down")));

        // when
        OpenClawAgentCreateRes response = agentProvisioningService.createAgent(1L, 10L, request);

        // then
        assertThat(response.status()).isEqualTo(AgentStatus.ERROR);
        assertThat(response.openClawAgentId()).isNull();
        verify(openClawGatewayClient, never()).setAgentFile(any(OpenClawAgentFileCommand.class));
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("일부 Skill file 동기화 실패 시 Agent를 SYNC_FAILED로 남긴다")
    void createAgent_skillFileSyncFailed_marksSyncFailed() {
        // given
        OpenClawAgentCreateReq request = new OpenClawAgentCreateReq(
                "Backend Agent",
                "/tmp/workspace",
                null,
                List.of(
                        new AgentSkillFileReq("IDENTITY.md", "identity"),
                        new AgentSkillFileReq("AGENTS.md", "agents")));
        givenWorkspaceAdmin();
        given(agentRepository.existsByWorkspaceIdAndName(1L, "Backend Agent")).willReturn(false);
        given(agentRepository.save(any(Agent.class))).willAnswer(agentSaveAnswer(100L));
        given(agentSkillFileRepository.save(any(AgentSkillFile.class))).willAnswer(skillFileSaveAnswer());
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.createAgent(any(OpenClawAgentCreateCommand.class)))
                .willReturn(new OpenClawAgentSummary("openclaw-agent-1", "workspace-1-agent-100"));
        doNothing()
                .doThrow(OpenClawGatewayException.rpcTimeout("agents.files.set", "req-2"))
                .when(openClawGatewayClient)
                .setAgentFile(any(OpenClawAgentFileCommand.class));

        // when
        OpenClawAgentCreateRes response = agentProvisioningService.createAgent(1L, 10L, request);

        // then
        assertThat(response.status()).isEqualTo(AgentStatus.SYNC_FAILED);
        assertThat(response.skillFiles())
                .extracting("syncStatus")
                .containsExactly(AgentSkillSyncStatus.SYNCED, AgentSkillSyncStatus.FAILED);
        assertThat(response.syncError()).contains("일부 Skill 파일");
    }

    @Test
    @DisplayName("같은 워크스페이스에 같은 이름의 Agent가 있으면 생성하지 않는다")
    void createAgent_duplicateName_throwsException() {
        // given
        OpenClawAgentCreateReq request = new OpenClawAgentCreateReq("Backend Agent", null, null, List.of());
        givenWorkspaceAdmin();
        given(agentRepository.existsByWorkspaceIdAndName(1L, "Backend Agent")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> agentProvisioningService.createAgent(1L, 10L, request))
                .isInstanceOf(ServiceException.class);
    }

    private void givenWorkspaceAdmin() {
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
    }

    private org.mockito.stubbing.Answer<Agent> agentSaveAnswer(Long id) {
        return invocation -> {
            Agent agent = invocation.getArgument(0);
            ReflectionTestUtils.setField(agent, "id", id);
            return agent;
        };
    }

    private org.mockito.stubbing.Answer<AgentSkillFile> skillFileSaveAnswer() {
        AtomicLong sequence = new AtomicLong(200L);
        return invocation -> {
            AgentSkillFile skillFile = invocation.getArgument(0);
            ReflectionTestUtils.setField(skillFile, "id", sequence.getAndIncrement());
            return skillFile;
        };
    }
}
