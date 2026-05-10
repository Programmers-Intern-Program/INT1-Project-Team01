package back.domain.agent.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.security.crypto.TinkCryptoUtil;
import jakarta.persistence.EntityManager;

@DataJpaTest
class AgentRepositoryTest {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private TinkCryptoUtil tinkCryptoUtil;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.createUser("agent-repo-sub", "agent-repo@test.com", "테스터"));
        workspace = workspaceRepository.save(Workspace.create("Agent Repository", "테스트", member));
    }

    @Test
    @DisplayName("CUSTOM category 조회 시 기존 NULL category Agent도 포함한다")
    void findFirstByCategory_customIncludesNullCategoryAgent() {
        // given
        Agent agent = Agent.create(workspace, "Legacy Agent", "~/.openclaw/workspace-1", 1L);
        agent.markOpenClawCreated("legacy-openclaw-agent");
        agent.markReady();
        Agent saved = agentRepository.saveAndFlush(agent);
        jdbcTemplate.update("UPDATE agents SET category = NULL WHERE id = ?", saved.getId());
        entityManager.clear();

        // when
        Agent found = agentRepository
                .findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        workspace.getId(), AgentCategory.CUSTOM, AgentStatus.READY)
                .orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getCategory()).isEqualTo(AgentCategory.CUSTOM);
    }

    @Test
    @DisplayName("Workspace의 READY Agent 목록은 OpenClaw Agent id가 있는 Agent만 id 오름차순으로 조회한다")
    void findByWorkspaceIdAndStatus_readyAgentsWithOpenClawIdOnly() {
        // given
        Agent orchestrator = Agent.create(
                workspace,
                "Main Orchestrator",
                AgentCategory.ORCHESTRATOR,
                "~/.openclaw/workspace-1",
                1L);
        orchestrator.markOpenClawCreated("openclaw-orchestrator");
        orchestrator.markReady();
        agentRepository.save(orchestrator);

        Agent backend = Agent.create(
                workspace,
                "Backend Agent",
                AgentCategory.BACKEND,
                "~/.openclaw/workspace-1",
                1L);
        backend.markOpenClawCreated("openclaw-backend");
        backend.markReady();
        agentRepository.save(backend);

        Agent creating = Agent.create(
                workspace,
                "Creating Agent",
                AgentCategory.QA,
                "~/.openclaw/workspace-1",
                1L);
        agentRepository.save(creating);

        Agent unsynced = Agent.create(
                workspace,
                "Unsynced Agent",
                AgentCategory.FRONTEND,
                "~/.openclaw/workspace-1",
                1L);
        unsynced.markReady();
        agentRepository.save(unsynced);

        // when
        var readyAgents = agentRepository
                .findByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        workspace.getId(), AgentStatus.READY);

        // then
        assertThat(readyAgents)
                .extracting(Agent::getName, Agent::getCategory, Agent::getOpenClawAgentId)
                .containsExactly(
                        tuple(
                                "Main Orchestrator", AgentCategory.ORCHESTRATOR, "openclaw-orchestrator"),
                        tuple("Backend Agent", AgentCategory.BACKEND, "openclaw-backend"));
    }
}
