package back.global.config.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "custom.cors")
public record CorsProperties(List<String> allowedOriginPatterns) {
    public CorsProperties {
        allowedOriginPatterns = List.copyOf(allowedOriginPatterns); // 방어적 복사
    }
}