package back.global.security;

import back.global.security.TokenAuthenticationException.TokenErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenAuthenticationExceptionTest {

    @Test
    @DisplayName("EXPIRED 타입 예외 생성")
    void constructor_expiredType_success() {
        // given & when
        TokenAuthenticationException exception = new TokenAuthenticationException(
                TokenErrorType.EXPIRED, "[test] expired", "만료된 토큰입니다.");

        // then
        assertThat(exception.tokenErrorType()).isEqualTo(TokenErrorType.EXPIRED);
    }

    @Test
    @DisplayName("INVALID 타입 예외 생성")
    void constructor_invalidType_success() {
        // given & when
        TokenAuthenticationException exception = new TokenAuthenticationException(
                TokenErrorType.INVALID, "[test] invalid", "유효하지 않은 토큰입니다.");

        // then
        assertThat(exception.tokenErrorType()).isEqualTo(TokenErrorType.INVALID);
    }
}
