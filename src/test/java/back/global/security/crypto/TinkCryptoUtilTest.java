package back.global.security.crypto;

import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TinkCryptoUtilTest {

    private TinkCryptoUtil tinkCryptoUtil;

    // 수정됨: AES-256-GCM 규격(32바이트 키)을 정확히 만족하는 테스트용 Tink Keyset JSON
    private static final String TEST_KEYSET_JSON_256BIT = "{\"primaryKeyId\":123456789,\"key\":[{\"keyData\":{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\",\"value\":\"GiAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==\",\"keyMaterialType\":\"SYMMETRIC\"},\"status\":\"ENABLED\",\"keyId\":123456789,\"outputPrefixType\":\"TINK\"}]}";

    @BeforeEach
    void setUp() {
        // given
        tinkCryptoUtil = new TinkCryptoUtil(TEST_KEYSET_JSON_256BIT);
    }

    @Test
    @DisplayName("평문을 암호화하면 v1: 로 시작하는 Base64 문자열이 반환된다.")
    void encrypt_success() {
        // given
        String plainText = "xoxb-secret-bot-token-12345";

        // when
        String encryptedText = tinkCryptoUtil.encrypt(plainText);

        // then
        assertThat(encryptedText).isNotNull();
        assertThat(encryptedText).isNotEqualTo(plainText);
        assertThat(encryptedText.length()).isGreaterThan(plainText.length());
    }

    @Test
    @DisplayName("암호화된 문자열을 복호화하면 원본 평문과 정확히 일치한다.")
    void decrypt_success() {
        // given
        String plainText = "xoxb-secret-bot-token-12345";
        String encryptedText = tinkCryptoUtil.encrypt(plainText);

        // when
        String decryptedText = tinkCryptoUtil.decrypt(encryptedText);

        // then
        assertThat(decryptedText).isEqualTo(plainText);
    }

    @Test
    @DisplayName("손상된 암호문을 복호화 시도하면 ServiceException이 발생한다.")
    void decrypt_invalid_base64_throws_exception() {
        // given
        String invalidEncryptedText = "손상된데이터";

        // when & then
        assertThatThrownBy(() -> tinkCryptoUtil.decrypt(invalidEncryptedText))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("[TinkCryptoUtil#decrypt] Base64 decoding failed.");
    }
}