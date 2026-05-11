package back.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.agent.dto.response.AgentInfoRes;
import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentSkillFile;
import back.domain.agent.entity.AgentSkillSyncStatus;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.agent.repository.AgentSkillFileRepository;
import back.domain.member.entity.Member;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class AgentQueryServiceImplTest {

    @Mock
    private WorkspaceAccessValidator workspaceAccessValidator;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentSkillFileRepository agentSkillFileRepository;

    @InjectMocks
    private AgentQueryServiceImpl agentQueryService;

    private Member member;
    private Workspace workspace;
    private WorkspaceMember workspaceMember;

    @BeforeEach
    void setUp() {
        member = Member.createUser("sub", "test@test.com", "관리자");
        ReflectionTestUtils.setField(member, "id", 10L);

        workspace = Workspace.create("AI Office", "설명", member);
        ReflectionTestUtils.setField(workspace, "id", 1L);

        workspaceMember = WorkspaceMember.create(workspace, member, WorkspaceMemberRole.MEMBER);
    }

    @Test
    @DisplayName("Workspace 멤버는 Agent 목록과 Skill 파일 동기화 상태를 조회할 수 있다")
    void listAgents_success() {
        // given
        Agent backendAgent = createAgent(100L, "Backend Agent", AgentCategory.BACKEND);
        backendAgent.markOpenClawCreated("openclaw-agent-1");
        backendAgent.markReady();
        Agent qaAgent = createAgent(101L, "QA Agent", AgentCategory.QA);
        qaAgent.markError("Gateway 연결 실패");

        AgentSkillFile skillFile = AgentSkillFile.create(backendAgent, "AGENTS.md", "instruction");
        ReflectionTestUtils.setField(skillFile, "id", 200L);
        skillFile.markSynced();

        given(workspaceAccessValidator.requireMember(1L, 10L)).willReturn(workspaceMember);
        given(agentRepository.findByWorkspaceIdOrderByIdAsc(1L)).willReturn(List.of(backendAgent, qaAgent));
        given(agentSkillFileRepository.findByAgentIdInOrderByAgentIdAscIdAsc(List.of(100L, 101L)))
                .willReturn(List.of(skillFile));

        // when
        List<AgentInfoRes> response = agentQueryService.listAgents(1L, 10L);

        // then
        assertThat(response).hasSize(2);
        assertThat(response.get(0).agentId()).isEqualTo(100L);
        assertThat(response.get(0).status()).isEqualTo(AgentStatus.READY);
        assertThat(response.get(0).skillFiles())
                .extracting("fileName", "syncStatus")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AGENTS.md", AgentSkillSyncStatus.SYNCED));
        assertThat(response.get(1).syncError()).isEqualTo("Gateway 연결 실패");
        verify(workspaceAccessValidator).requireMember(1L, 10L);
    }

    @Test
    @DisplayName("Workspace 멤버는 Agent 상세 정보를 조회할 수 있다")
    void getAgent_success() {
        // given
        Agent agent = createAgent(100L, "Backend Agent", AgentCategory.BACKEND);
        AgentSkillFile skillFile = AgentSkillFile.create(agent, "AGENTS.md", "instruction");
        ReflectionTestUtils.setField(skillFile, "id", 200L);
        given(workspaceAccessValidator.requireMember(1L, 10L)).willReturn(workspaceMember);
        given(agentRepository.findByIdAndWorkspaceId(100L, 1L)).willReturn(Optional.of(agent));
        given(agentSkillFileRepository.findByAgentIdOrderByIdAsc(100L)).willReturn(List.of(skillFile));

        // when
        AgentInfoRes response = agentQueryService.getAgent(1L, 10L, 100L);

        // then
        assertThat(response.agentId()).isEqualTo(100L);
        assertThat(response.workspaceId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Backend Agent");
        assertThat(response.skillFiles()).hasSize(1);
    }

    @Test
    @DisplayName("Agent가 없으면 ServiceException이 발생한다")
    void getAgent_missingAgent_throwsException() {
        // given
        given(workspaceAccessValidator.requireMember(1L, 10L)).willReturn(workspaceMember);
        given(agentRepository.findByIdAndWorkspaceId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agentQueryService.getAgent(1L, 10L, 999L))
                .isInstanceOf(ServiceException.class)
                .extracting("clientMessage")
                .isEqualTo("Agent를 찾을 수 없습니다.");
    }

    private Agent createAgent(Long id, String name, AgentCategory category) {
        Agent agent = Agent.create(workspace, name, category, "~/.openclaw/workspace-1", 10L);
        ReflectionTestUtils.setField(agent, "id", id);
        return agent;
    }
}
