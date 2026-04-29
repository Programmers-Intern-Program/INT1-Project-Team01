package back.global.security;

import back.global.security.TokenAuthenticationException.TokenErrorType;
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

@Component
public final class JwtTokenProvider {
    private static final String ROLE_CLAIM = "role";
    private static final String EMAIL_CLAIM = "email";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtTokenProvider(
            @Value("${custom.jwt.secret-key}") String secret,
            @Value("${custom.jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds,
            @Value("${custom.jwt.refresh-token-expiration-seconds}") long refreshTokenExpirationSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = accessTokenExpirationSeconds * 1000;
        this.refreshTokenExpirationMillis = refreshTokenExpirationSeconds * 1000;
    }

    public String generateAccessToken(long memberId, String email, String role) {
        return generateToken(memberId, email, role, accessTokenExpirationMillis, ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshToken(long memberId, String email, String role) {
        return generateToken(memberId, email, role, refreshTokenExpirationMillis, REFRESH_TOKEN_TYPE);
    }

    public AccessTokenPayload getAccessTokenPayload(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, ACCESS_TOKEN_TYPE);

        return new AccessTokenPayload(parseMemberId(claims), parseMemberRole(claims));
    }

    public long getMemberIdFromAccessToken(String token) {
        return getAccessTokenPayload(token).memberId();
    }

    public String getMemberRoleFromAccessToken(String token) {
        return getAccessTokenPayload(token).role();
    }

    public long getMemberIdFromRefreshToken(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, REFRESH_TOKEN_TYPE);
        return parseMemberId(claims);
    }

    private long parseMemberId(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new TokenAuthenticationException(
                    TokenErrorType.INVALID,
                    "[JwtTokenProvider#parseMemberId] token subject is not a valid number",
                    "유효하지 않은 토큰입니다.");
        }
    }

    private String generateToken(long memberId, String email, String role, long expirationMillis, String tokenType) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .claim(EMAIL_CLAIM, email)
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    private String parseMemberRole(Claims claims) {
        Object roleClaim = claims.get(ROLE_CLAIM);
        if (!(roleClaim instanceof String roleValue) || roleValue.isBlank()) {
            throw new TokenAuthenticationException(
                    TokenErrorType.INVALID,
                    "[JwtTokenProvider#parseMemberRole] role claim is missing or blank",
                    "유효하지 않은 토큰입니다.");
        }
        return roleValue;
    }

    private void validateTokenType(Claims claims, String expectedTokenType) {
        Object tokenType = claims.get(TOKEN_TYPE_CLAIM);
        if (!(tokenType instanceof String tokenTypeValue) || !expectedTokenType.equals(tokenTypeValue)) {
            throw new TokenAuthenticationException(
                    TokenErrorType.INVALID,
                    "[JwtTokenProvider#validateTokenType] token type is invalid or mismatched",
                    "유효하지 않은 토큰입니다.");
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenAuthenticationException(
                    TokenErrorType.EXPIRED,
                    "[JwtTokenProvider#parseClaims] token is expired",
                    "만료된 토큰입니다.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenAuthenticationException(
                    TokenErrorType.INVALID,
                    "[JwtTokenProvider#parseClaims] token parsing failed",
                    "유효하지 않은 토큰입니다.");
        }
    }

    public record AccessTokenPayload(long memberId, String role) {}
}
