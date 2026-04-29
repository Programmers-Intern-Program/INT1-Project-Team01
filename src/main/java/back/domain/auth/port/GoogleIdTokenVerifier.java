package back.domain.auth.port;

public interface GoogleIdTokenVerifier {
    GoogleUserInfo verify(String idToken);

    record GoogleUserInfo(String googleSub, String email, String name) {}
}
