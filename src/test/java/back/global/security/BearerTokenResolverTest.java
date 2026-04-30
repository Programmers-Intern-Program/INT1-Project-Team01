package back.global.security;

import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class BearerTokenResolverTest {

    private BearerTokenResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BearerTokenResolver();
    }

    @Test
    @DisplayName("정상 Bearer 토큰 파싱 성공")
    void resolve_validBearerToken_success() {
        // when
        String result = resolver.resolve("Bearer valid-token");

        // then
        assertThat(result).isEqualTo("valid-token");
    }

    @Test
    @DisplayName("null 헤더 시 예외")
    void resolve_nullHeader_throwsException() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("빈 문자열 헤더 시 예외")
    void resolve_blankHeader_throwsException() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve(""))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("Bearer 접두사 없는 헤더 시 예외")
    void resolve_noBearerPrefix_throwsException() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve("Basic abc123"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("Bearer 뒤 토큰 없는 경우 예외")
    void resolve_emptyTokenAfterBearer_throwsException() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve("Bearer "))
                .isInstanceOf(ServiceException.class);
    }
}
