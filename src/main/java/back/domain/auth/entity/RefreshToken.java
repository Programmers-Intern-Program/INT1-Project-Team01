package back.domain.auth.entity;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "token", nullable = false, length = 1000)
    private String token;

    private RefreshToken(Long memberId, String token) {
        this.memberId = memberId;
        this.token = token;
    }

    public static RefreshToken issue(Long memberId, String token) {
        Long validatedMemberId = requireNotNull(memberId, "memberId");
        String validatedToken = requireNotBlank(token, "token");
        return new RefreshToken(validatedMemberId, validatedToken);
    }

    public void rotate(String token) {
        String validatedToken = requireNotBlank(token, "token");
        this.token = validatedToken;
    }

    public boolean matches(String token) {
        return this.token.equals(token);
    }

    private static Long requireNotNull(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
