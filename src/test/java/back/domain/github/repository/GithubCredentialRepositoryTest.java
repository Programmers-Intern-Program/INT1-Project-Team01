package back.domain.github.repository;

import back.domain.github.entity.GithubCredential;
import back.global.security.crypto.TinkCryptoConverter;
import back.global.security.crypto.TinkCryptoUtil;
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
class GithubCredentialRepositoryTest {

    @Autowired
    private GithubCredentialRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("엔티티에 평문 GitHub PAT를 넣고 저장하면, 실제 DB 컬럼에는 암호화되어 저장된다.")
    void convertToDatabaseColumn_encryption_verify() {
        // given
        String plainToken = "ghp_real-plain-github-token-for-test123";

        GithubCredential credential = GithubCredential.builder()
                .workspaceId(1L)
                .displayName("BE-Server-Access")
                .token(plainToken)
                .createdByMemberId(100L)
                .build();

        // when
        GithubCredential savedCredential = repository.saveAndFlush(credential);

        // then 1: 어플리케이션(JPA) 레벨에서는 평문으로 정상 조회
        assertThat(savedCredential.getToken()).isEqualTo(plainToken);

        // then 2: Native Query로 실제 DB 테이블을 직접 조회하면 평문이 아님을 증명 (DoD 충족)
        String rawDbToken = jdbcTemplate.queryForObject(
                "SELECT token_encrypted FROM github_credentials WHERE id = ?",
                String.class,
                savedCredential.getId()
        );

        assertThat(rawDbToken).isNotNull();
        assertThat(rawDbToken).isNotEqualTo(plainToken); // DB에는 평문이 아님을 확인
    }
}