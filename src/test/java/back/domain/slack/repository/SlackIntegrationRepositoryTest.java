package back.domain.slack.repository;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.slack.entity.SlackIntegration;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.security.crypto.TinkCryptoConverter;
import back.global.security.crypto.TinkCryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TinkCryptoUtil.class, TinkCryptoConverter.class})
@TestPropertySource(properties = {
        "security.encryption.tink-keyset={\"primaryKeyId\":123456789,\"key\":[{\"keyData\":{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\",\"value\":\"GiAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==\",\"keyMaterialType\":\"SYMMETRIC\"},\"status\":\"ENABLED\",\"keyId\":123456789,\"outputPrefixType\":\"TINK\"}]}"
})
class SlackIntegrationRepositoryTest {

    @Autowired
    private SlackIntegrationRepository repository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.createUser("sub", "test@test.com", "홍길동"));
        workspace = workspaceRepository.save(Workspace.create("테스트 워크스페이스", "설명", member));
    }

    @Test
    @DisplayName("엔티티에 평문 토큰을 넣고 저장하면, 실제 DB 컬럼에는 암호화되어 저장된다.")
    void convertToDatabaseColumn_encryption_verify() {
        // given
        String plainBotToken = "xoxb-real-plain-token";
        String plainSigningSecret = "real-signing-secret";

        SlackIntegration integration = SlackIntegration.builder()
                .workspace(workspace)
                .slackTeamId("T12345")
                .slackChannelId("C12345")
                .botToken(plainBotToken)
                .signingSecret(plainSigningSecret)
                .createdByMemberId(100L)
                .build();

        // when
        SlackIntegration savedIntegration = repository.saveAndFlush(integration);

        // then 1
        assertThat(savedIntegration.getBotToken()).isEqualTo(plainBotToken);

        // then 2
        String rawDbToken = jdbcTemplate.queryForObject(
                "SELECT bot_token_encrypted FROM slack_integrations WHERE id = ?",
                String.class,
                savedIntegration.getId()
        );

        assertThat(rawDbToken).isNotNull();
        assertThat(rawDbToken).isNotEqualTo(plainBotToken);
    }
}