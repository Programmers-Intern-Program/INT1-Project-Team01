package back.domain.auth.service;

import back.domain.auth.dto.response.GoogleLoginResponse;
import back.domain.auth.dto.response.RefreshAuthTokenResponse;
import back.domain.auth.entity.RefreshToken;
import back.domain.auth.port.GoogleIdTokenVerifier;
import back.domain.auth.repository.RefreshTokenRepository;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.global.exception.ServiceException;
import back.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private MemberRepository memberRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "adminAllowlistEmails", "");
    }

    @Test
    @DisplayName("구글 로그인 - 신규 회원 성공")
    void loginWithGoogle_newMember_success() {
        // given
        GoogleIdTokenVerifier.GoogleUserInfo userInfo =
                new GoogleIdTokenVerifier.GoogleUserInfo("sub123", "new@test.com", "신규유저");
        Member member = Member.createUser("sub123", "new@test.com", "신규유저");
        ReflectionTestUtils.setField(member, "id", 1L);

        given(googleIdTokenVerifier.verify("idToken")).willReturn(userInfo);
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        given(memberRepository.findByGoogleSub("sub123")).willReturn(Optional.empty());
        given(memberRepository.findByEmail("new@test.com")).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(member);
        given(jwtTokenProvider.generateAccessToken(anyLong(), anyString(), anyString())).willReturn("access");
        given(jwtTokenProvider.generateRefreshToken(anyLong(), anyString(), anyString())).willReturn("refresh");
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.empty());

        // when
        GoogleLoginResponse response = authService.loginWithGoogle("idToken");

        // then
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("구글 로그인 - 기존 회원 성공")
    void loginWithGoogle_existingMember_success() {
        // given
        GoogleIdTokenVerifier.GoogleUserInfo userInfo =
                new GoogleIdTokenVerifier.GoogleUserInfo("sub123", "existing@test.com", "기존유저");
        Member member = Member.createUser("sub123", "existing@test.com", "기존유저");
        ReflectionTestUtils.setField(member, "id", 1L);
        RefreshToken existingToken = RefreshToken.issue(1L, "oldToken");

        given(googleIdTokenVerifier.verify("idToken")).willReturn(userInfo);
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        given(memberRepository.findByGoogleSub("sub123")).willReturn(Optional.of(member));
        given(memberRepository.save(any(Member.class))).willReturn(member);
        given(jwtTokenProvider.generateAccessToken(anyLong(), anyString(), anyString())).willReturn("access");
        given(jwtTokenProvider.generateRefreshToken(anyLong(), anyString(), anyString())).willReturn("newRefresh");
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of(existingToken));

        // when
        GoogleLoginResponse response = authService.loginWithGoogle("idToken");

        // then
        assertThat(response.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void refresh_success() {
        // given
        Member member = Member.createUser("sub", "test@test.com", "홍길동");
        ReflectionTestUtils.setField(member, "id", 1L);

        given(jwtTokenProvider.getMemberIdFromRefreshToken("oldRefresh")).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(jwtTokenProvider.generateAccessToken(anyLong(), anyString(), anyString())).willReturn("newAccess");
        given(jwtTokenProvider.generateRefreshToken(anyLong(), anyString(), anyString())).willReturn("newRefresh");
        given(refreshTokenRepository.rotateIfMatch(1L, "oldRefresh", "newRefresh")).willReturn(1);

        // when
        RefreshAuthTokenResponse response = authService.refresh("oldRefresh");

        // then
        assertThat(response.accessToken()).isEqualTo("newAccess");
        assertThat(response.refreshToken()).isEqualTo("newRefresh");
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 토큰 불일치")
    void refresh_tokenMismatch_throwsException() {
        // given
        Member member = Member.createUser("sub", "test@test.com", "홍길동");
        ReflectionTestUtils.setField(member, "id", 1L);

        given(jwtTokenProvider.getMemberIdFromRefreshToken("oldRefresh")).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(jwtTokenProvider.generateAccessToken(anyLong(), anyString(), anyString())).willReturn("newAccess");
        given(jwtTokenProvider.generateRefreshToken(anyLong(), anyString(), anyString())).willReturn("newRefresh");
        given(refreshTokenRepository.rotateIfMatch(1L, "oldRefresh", "newRefresh")).willReturn(0);

        // when & then
        assertThatThrownBy(() -> authService.refresh("oldRefresh"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 회원 없음")
    void refresh_memberNotFound_throwsException() {
        // given
        given(jwtTokenProvider.getMemberIdFromRefreshToken("oldRefresh")).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.refresh("oldRefresh"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() {
        // given
        RefreshToken storedToken = RefreshToken.issue(1L, "storedToken");

        given(jwtTokenProvider.getMemberIdFromRefreshToken("storedToken")).willReturn(1L);
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of(storedToken));

        // when & then
        assertThatCode(() -> authService.logout(1L, "storedToken"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("로그아웃 실패 - 본인 토큰 아님")
    void logout_tokenOwnerMismatch_throwsException() {
        // given
        given(jwtTokenProvider.getMemberIdFromRefreshToken("otherToken")).willReturn(2L);

        // when & then
        assertThatThrownBy(() -> authService.logout(1L, "otherToken"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("로그아웃 실패 - 저장된 토큰 없음")
    void logout_storedTokenNotFound_throwsException() {
        // given
        given(jwtTokenProvider.getMemberIdFromRefreshToken("token")).willReturn(1L);
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.logout(1L, "token"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("로그아웃 실패 - 저장된 토큰과 불일치")
    void logout_tokenValueMismatch_throwsException() {
        // given
        RefreshToken storedToken = RefreshToken.issue(1L, "storedToken");

        given(jwtTokenProvider.getMemberIdFromRefreshToken("differentToken")).willReturn(1L);
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of(storedToken));

        // when & then
        assertThatThrownBy(() -> authService.logout(1L, "differentToken"))
                .isInstanceOf(ServiceException.class);
    }
}
