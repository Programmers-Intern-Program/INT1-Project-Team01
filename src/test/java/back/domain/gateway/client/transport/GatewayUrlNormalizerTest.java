package back.domain.gateway.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatewayUrlNormalizerTest {

    private final GatewayUrlNormalizer normalizer = new GatewayUrlNormalizer();

    @Test
    @DisplayName("HTTP Gateway URL은 WebSocket URL로 변환한다")
    void toWebSocketUri_httpUrl_success() {
        // when & then
        assertThat(normalizer.toWebSocketUri("http://localhost:3000/gateway").toString())
                .isEqualTo("ws://localhost:3000/gateway");
        assertThat(normalizer.toWebSocketUri("https://gateway.example/ws").toString())
                .isEqualTo("wss://gateway.example/ws");
    }

    @Test
    @DisplayName("이미 WebSocket scheme이면 그대로 사용한다")
    void toWebSocketUri_webSocketUrl_success() {
        // when & then
        assertThat(normalizer.toWebSocketUri("ws://localhost:3000").toString())
                .isEqualTo("ws://localhost:3000");
        assertThat(normalizer.toWebSocketUri("wss://gateway.example").toString())
                .isEqualTo("wss://gateway.example");
    }

    @Test
    @DisplayName("지원하지 않는 Gateway URL scheme은 거부한다")
    void toWebSocketUri_unsupportedScheme_throwsException() {
        // when & then
        assertThatThrownBy(() -> normalizer.toWebSocketUri("ftp://gateway.example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    @DisplayName("scheme이 없는 Gateway URL은 거부한다")
    void toWebSocketUri_missingScheme_throwsException() {
        // when & then
        assertThatThrownBy(() -> normalizer.toWebSocketUri("gateway.example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    @DisplayName("host가 없는 Gateway URL은 거부한다")
    void toWebSocketUri_missingHost_throwsException() {
        // when & then
        assertThatThrownBy(() -> normalizer.toWebSocketUri("https:///gateway"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    @DisplayName("문법이 잘못된 Gateway URL은 거부한다")
    void toWebSocketUri_invalidSyntax_throwsException() {
        // when & then
        assertThatThrownBy(() -> normalizer.toWebSocketUri("https://gateway example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("syntax");
    }

    @Test
    @DisplayName("fragment가 포함된 Gateway URL은 거부한다")
    void toWebSocketUri_fragment_throwsException() {
        // when & then
        assertThatThrownBy(() -> normalizer.toWebSocketUri("https://gateway.example#token=abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fragment");
    }
}
