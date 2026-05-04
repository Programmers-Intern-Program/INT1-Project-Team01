package back.domain.github.dto.response;

import back.domain.github.entity.GithubCredential;

public record GithubCredentialInfoRes(
        Long id,
        String displayName,
        String maskedToken
) {
    private static final int MIN_TOKEN_LENGTH_FOR_MASKING = 8;  // 마스킹(별표) 처리를 적용하기 위한 토큰의 최소 길이
    private static final int VISIBLE_PREFIX_LENGTH = 4;     // 토큰 앞부분의 평문 노출 길이 (예: ghp_)
    private static final int VISIBLE_SUFFIX_LENGTH = 4;     // 토큰 뒷부분의 평문 노출 길이
    private static final String MASK_STRING = "****";       // 중간 마스킹에 사용될 문자열


    public static GithubCredentialInfoRes from(GithubCredential entity) {
        return new GithubCredentialInfoRes(
                entity.getId(),
                entity.getDisplayName(),
                maskToken(entity.getToken())
        );
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= MIN_TOKEN_LENGTH_FOR_MASKING) {
            return MASK_STRING;
        }
        return token.substring(0, VISIBLE_PREFIX_LENGTH) +
                MASK_STRING +
                token.substring(token.length() - VISIBLE_SUFFIX_LENGTH);
    }
}