package back.domain.auth.service;

import back.domain.auth.dto.response.GoogleLoginResponse;
import back.domain.auth.dto.response.RefreshAuthTokenResponse;
import back.domain.auth.entity.RefreshToken;
import back.domain.auth.port.GoogleIdTokenVerifier;
import back.domain.auth.repository.RefreshTokenRepository;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {
    private static final String INVALID_REFRESH_MESSAGE = "유효하지 않은 토큰입니다.";
    private static final String TOKEN_OWNER_MISMATCH_MESSAGE = "본인 토큰이 아닙니다.";
    private static final String MEMBER_NOT_FOUND_MESSAGE = "회원이 존재하지 않습니다.";
    private static final String EMAIL_CONFLICT_MESSAGE = "이미 사용 중인 이메일입니다.";

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final TransactionTemplate transactionTemplate;

    @Value("${ADMIN_ALLOWLIST_EMAILS:}")
    private String adminAllowlistEmails;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GoogleLoginResponse loginWithGoogle(String idToken) {
        GoogleIdTokenVerifier.GoogleUserInfo googleUserInfo = googleIdTokenVerifier.verify(idToken);
        return transactionTemplate.execute(status -> loginWithVerifiedGoogleUser(googleUserInfo));
    }

    @Override
    @Transactional
    public RefreshAuthTokenResponse refresh(String refreshToken) {
        long memberId = jwtTokenProvider.getMemberIdFromRefreshToken(refreshToken);
        Member member = getMemberOrThrow(memberId);

        String accessToken =
                jwtTokenProvider.generateAccessToken(member.getId(), member.getEmail(), member.getRole().name());
        String rotatedRefreshToken =
                jwtTokenProvider.generateRefreshToken(member.getId(), member.getEmail(), member.getRole().name());
        int updatedRows = refreshTokenRepository.rotateIfMatch(memberId, refreshToken, rotatedRefreshToken);
        if (updatedRows == 0) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[AuthServiceImpl#refresh] conditional refresh token rotate failed",
                    INVALID_REFRESH_MESSAGE);
        }

        return new RefreshAuthTokenResponse(accessToken, rotatedRefreshToken);
    }

    @Override
    @Transactional
    public void logout(long authenticatedMemberId, String refreshToken) {
        long tokenOwnerId = jwtTokenProvider.getMemberIdFromRefreshToken(refreshToken);
        if (authenticatedMemberId != tokenOwnerId) {
            throw new ServiceException(
                    CommonErrorCode.FORBIDDEN,
                    "[AuthServiceImpl#logout] refresh token owner and authenticated member do not match",
                    TOKEN_OWNER_MISMATCH_MESSAGE);
        }

        RefreshToken storedRefreshToken = getStoredRefreshTokenOrThrow(tokenOwnerId);
        validateRefreshTokenMatch(storedRefreshToken, refreshToken);
        refreshTokenRepository.deleteByMemberId(authenticatedMemberId);
    }

    private Member upsertMember(String googleSub, String email, String name) {
        return memberRepository
                .findByGoogleSub(googleSub)
                .map(existingMember -> updateExistingMember(existingMember, email, name))
                .orElseGet(() -> createNewMember(googleSub, email, name));
    }

    private GoogleLoginResponse loginWithVerifiedGoogleUser(GoogleIdTokenVerifier.GoogleUserInfo googleUserInfo) {
        Member member = upsertMember(googleUserInfo.googleSub(), googleUserInfo.email(), googleUserInfo.name());
        String role = member.getRole().name();

        String accessToken = jwtTokenProvider.generateAccessToken(member.getId(), member.getEmail(), role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId(), member.getEmail(), role);
        saveOrRotateRefreshToken(member.getId(), refreshToken);

        return new GoogleLoginResponse(
                member.getId(), member.getName(), member.getEmail(), role, accessToken, refreshToken);
    }

    private Member updateExistingMember(Member existingMember, String email, String name) {
        if (!existingMember.getEmail().equals(email)) {
            validateEmailConflict(email, existingMember.getId());
            existingMember.updateEmail(email);
        }

        if (!existingMember.getName().equals(name)) {
            existingMember.updateName(name);
        }

        if (isAdminAllowlistEmail(email) && !existingMember.isAdmin()) {
            existingMember.promoteToAdmin();
        }

        return memberRepository.save(existingMember);
    }

    private Member createNewMember(String googleSub, String email, String name) {
        validateEmailConflict(email, null);
        Member createdMember =
                isAdminAllowlistEmail(email)
                        ? Member.createAdmin(googleSub, email, name)
                        : Member.createUser(googleSub, email, name);
        return memberRepository.save(createdMember);
    }

    private void validateEmailConflict(String email, Long currentMemberId) {
        memberRepository.findByEmail(email).ifPresent(existingByEmail -> {
            if (currentMemberId == null || !currentMemberId.equals(existingByEmail.getId())) {
                throw new ServiceException(
                        CommonErrorCode.CONFLICT,
                        "[AuthServiceImpl#validateEmailConflict] email is already assigned",
                        EMAIL_CONFLICT_MESSAGE);
            }
        });
    }

    private boolean isAdminAllowlistEmail(String email) {
        if (adminAllowlistEmails == null || adminAllowlistEmails.isBlank()) {
            return false;
        }

        String normalizedEmail = normalizeEmail(email);
        return Arrays.stream(adminAllowlistEmails.split(","))
                .map(String::trim)
                .filter(candidate -> !candidate.isBlank())
                .map(this::normalizeEmail)
                .anyMatch(normalizedEmail::equals);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void saveOrRotateRefreshToken(long memberId, String refreshToken) {
        refreshTokenRepository.findByMemberId(memberId).ifPresentOrElse(existingRefreshToken -> {
            existingRefreshToken.rotate(refreshToken);
            refreshTokenRepository.save(existingRefreshToken);
        }, () -> refreshTokenRepository.save(RefreshToken.issue(memberId, refreshToken)));
    }

    private Member getMemberOrThrow(long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new ServiceException(
                CommonErrorCode.NOT_FOUND,
                "[AuthServiceImpl#getMemberOrThrow] member not found by id",
                MEMBER_NOT_FOUND_MESSAGE));
    }

    private RefreshToken getStoredRefreshTokenOrThrow(long memberId) {
        return refreshTokenRepository.findByMemberId(memberId).orElseThrow(() -> new ServiceException(
                CommonErrorCode.UNAUTHORIZED,
                "[AuthServiceImpl#getStoredRefreshTokenOrThrow] stored refresh token not found",
                INVALID_REFRESH_MESSAGE));
    }

    private void validateRefreshTokenMatch(RefreshToken storedRefreshToken, String refreshToken) {
        if (!storedRefreshToken.matches(refreshToken)) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[AuthServiceImpl#validateRefreshTokenMatch] refresh token mismatch",
                    INVALID_REFRESH_MESSAGE);
        }
    }
}
