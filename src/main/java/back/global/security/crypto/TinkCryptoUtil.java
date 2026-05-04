package back.global.security.crypto;

import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Google Tink 기반의 데이터 암호화 및 복호화를 수행하는 유틸리티 클래스입니다.
 * <p>
 * JCA(Java Cryptography Architecture)의 IV 수동 관리 등 취약점을 방지하기 위해
 * Google Tink의 AEAD(인증된 암호화) 인터페이스를 사용하여 AES-256-GCM 암호화를 수행합니다.
 * 암호화된 결과는 데이터베이스 저장을 위해 Base64로 인코딩되어 반환됩니다.
 *
 * <p><b>주요 생성자:</b><br>
 * {@code TinkCryptoUtil(String tinkKeysetJson)} <br>
 * 환경변수로부터 주입받은 JSON 형태의 Tink Keyset을 이용하여 KeysetHandle 및 Aead 인스턴스를 초기화합니다. <br>
 *
 * <p><b>빈 관리:</b><br>
 * {@code @Component} 어노테이션을 통해 스프링 컨테이너에 의해 싱글톤 빈으로 관리됩니다.
 *
 * <p><b>외부 모듈:</b><br>
 * Google Tink (com.google.crypto.tink:tink) 라이브러리를 의존합니다.
 *
 * @author minhee
 * @see com.google.crypto.tink.Aead
 * @since 2026-04-30
 */

// TODO: 보안을 위한 OCI Vault 고려 [IT-9]
@Component
public final class TinkCryptoUtil {

    private final Aead aead;

    public TinkCryptoUtil(@Value("${security.encryption.tink-keyset}") String tinkKeysetJson) {
        try {
            AeadConfig.register();

            KeysetHandle keysetHandle = CleartextKeysetHandle.read(
                    JsonKeysetReader.withString(tinkKeysetJson)
            );

            this.aead = keysetHandle.getPrimitive(Aead.class);
        } catch (Exception e) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[TinkCryptoUtil#constructor] Failed to initialize Tink KeysetHandle. " + e.getMessage(),
                    "암호화 모듈 초기화 중 시스템 오류가 발생했습니다."
            );
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] cipherTextBytes = aead.encrypt(plainText.getBytes(StandardCharsets.UTF_8), new byte[0]);
            return Base64.getEncoder().encodeToString(cipherTextBytes);
        } catch (Exception e) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[TinkCryptoUtil#encrypt] Tink encryption failed. " + e.getMessage(),
                    "데이터 암호화 처리 중 시스템 오류가 발생했습니다."
            );
        }
    }

    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isBlank()) {
            return null;
        }
        try {
            byte[] cipherTextBytes = Base64.getDecoder().decode(encryptedData);
            byte[] plainTextBytes = aead.decrypt(cipherTextBytes, new byte[0]);

            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[TinkCryptoUtil#decrypt] Base64 decoding failed.",
                    "데이터 형식이 손상되었습니다."
            );
        } catch (Exception e) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[TinkCryptoUtil#decrypt] Tink decryption failed. " + e.getMessage(),
                    "데이터 복호화 처리 중 시스템 오류가 발생했습니다."
            );
        }
    }
}