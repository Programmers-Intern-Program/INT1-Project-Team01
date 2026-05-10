package back.domain.agent.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
}
