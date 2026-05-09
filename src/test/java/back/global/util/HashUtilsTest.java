package back.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HashUtilsTest {

    @Test
    @DisplayName("SHA-256 해시를 hex 문자열로 반환한다")
    void sha256Hex_validInput_returnsHex() {
        // when
        String result = HashUtils.sha256Hex("slack-source-ref");

        // then
        assertThat(result).isEqualTo("b69ce7dee0231944279d9649b3874d6f3db1773267730cd51d35cad1a18af3f7");
    }

    @Test
    @DisplayName("SHA-256 해시 대상 값이 null이면 예외가 발생한다")
    void sha256Hex_nullInput_throwsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HashUtils.sha256Hex(null))
                .withMessage("value must not be null");
    }
}
