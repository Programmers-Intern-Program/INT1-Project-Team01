package back.domain.slack.controller;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.slack.client.SlackClient;
import back.domain.slack.dto.response.SlackOAuthAccessRes;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.security.OAuthStateTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slack OAuth 2.0 연동 파이프라인 전체를 검증하는 통합 테스트입니다.
 * 외부 API(Slack) 통신만 Mocking하고, 실제 Provider와 DB(H2)를 사용하여 흐름을 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SlackOAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthStateTokenProvider oauthStateTokenProvider;

    @Autowired
    private SlackIntegrationRepository slackIntegrationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @MockitoBean
    private SlackClient slackClient;

    @Test
    @DisplayName("정상적인 OAuth 콜백 요청이 오면, JWT를 검증하고 토큰을 교환한 뒤 DB에 연동 정보를 저장하고 프론트엔드로 리다이렉트한다.")
    void oauthCallbackFlow_success() throws Exception {
        // given
        Member member = memberRepository.save(
                Member.createAdmin("test-google-sub", "test@example.com", "Test User")
        );

        Workspace workspace = workspaceRepository.save(
                Workspace.create("테스트 워크스페이스", "워크스페이스 설명", member)
        );

        workspaceMemberRepository.save(
                WorkspaceMember.create(workspace, member, WorkspaceMemberRole.ADMIN)
        );

        Long workspaceId = workspace.getId();
        Long memberId = member.getId();
        String validCode = "valid-auth-code";

        String validState = oauthStateTokenProvider.generateOAuthState(workspaceId, memberId);

        SlackOAuthAccessRes.Team team = new SlackOAuthAccessRes.Team("T999", "Test Team");
        SlackOAuthAccessRes.IncomingWebhook webhook = new SlackOAuthAccessRes.IncomingWebhook("C999", "https://slack.webhook.url");
        SlackOAuthAccessRes mockSlackResponse = new SlackOAuthAccessRes(true, "xoxb-real-bot-token", team, webhook, null);

        given(slackClient.exchangeToken(eq(validCode), any(), any(), any())).willReturn(mockSlackResponse);

        // when & then
        mockMvc.perform(get("/api/v1/slack/oauth/callback")
                        .param("code", validCode)
                        .param("state", validState))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/settings/integrations/success")));

        List<SlackIntegration> savedIntegrations = slackIntegrationRepository.findAllByWorkspaceId(workspaceId);

        assertThat(savedIntegrations).hasSize(1);
        SlackIntegration savedEntity = savedIntegrations.getFirst();
        assertThat(savedEntity.getSlackTeamId()).isEqualTo("T999");
        assertThat(savedEntity.getSlackChannelId()).isEqualTo("C999");
        assertThat(savedEntity.getBotToken()).isEqualTo("xoxb-real-bot-token");
        assertThat(savedEntity.getCreatedByMemberId()).isEqualTo(memberId);
    }
}