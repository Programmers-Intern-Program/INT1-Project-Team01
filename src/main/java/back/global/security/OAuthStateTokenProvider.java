package back.global.security;

import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * OAuth 연동 과정에서 위변조 및 Replay Attack 방지를 위한
 * State 검증 전용 JWT 토큰 프로바이더입니다.
 */

@Component
public final class OAuthStateTokenProvider {

    private static final String WORKSPACE_ID_CLAIM = "workspaceId";
    private static final String MEMBER_ID_CLAIM = "memberId";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String OAUTH_STATE_TOKEN_TYPE = "OAUTH_STATE";
    private static final long EXPIRATION_MILLIS = 5 * 60 * 1000; // 유효시간 5분

    private final SecretKey secretKey;

    public OAuthStateTokenProvider(@Value("${custom.slack.oauth-state-secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateOAuthState(Long workspaceId, Long memberId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MILLIS);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim(WORKSPACE_ID_CLAIM, workspaceId)
                .claim(MEMBER_ID_CLAIM, memberId)
                .claim(TOKEN_TYPE_CLAIM, OAUTH_STATE_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public OAuthStatePayload parseOAuthState(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[OAuthStateTokenProvider#parseOAuthState] Token expired",
                    "인증 요청 시간이 초과되었습니다. 처음부터 다시 시도해주세요."
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[OAuthStateTokenProvider#parseOAuthState] Invalid token signature or format",
                    "유효하지 않은 보안 식별자입니다."
            );
        }

        Object tokenType = claims.get(TOKEN_TYPE_CLAIM);
        if (!OAUTH_STATE_TOKEN_TYPE.equals(tokenType)) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[OAuthStateTokenProvider#parseOAuthState] Mismatched token type",
                    "잘못된 토큰 타입입니다."
            );
        }

        Long workspaceId = claims.get(WORKSPACE_ID_CLAIM, Long.class);
        Long memberId = claims.get(MEMBER_ID_CLAIM, Long.class);

        if (workspaceId == null || memberId == null) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[OAuthStateTokenProvider#parseOAuthState] Missing essential claims",
                    "상태 값에 필수 정보가 누락되었습니다."
            );
        }

        return new OAuthStatePayload(workspaceId, memberId);
    }

    public record OAuthStatePayload(Long workspaceId, Long memberId) {}
}