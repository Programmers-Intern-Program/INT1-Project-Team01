package back.domain.github.entity;

import back.global.jpa.entity.BaseEntity;
import back.global.security.crypto.TinkCryptoConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Workspace 내에서 GitHub Repository 접근을 위해 사용되는 Personal Access Token(PAT) 정보를 저장하는 도메인 엔티티입니다.
 * <p>
 * 사용자가 등록한 GitHub PAT 원문을 {@code TinkCryptoConverter}를 통해 데이터베이스에 안전하게 암호화하여 저장합니다.
 * 토큰 원문은 절대로 평문으로 저장되거나 로그 및 API 응답에 노출되어서는 안 됩니다.
 *
 * <p><b>상속 정보:</b><br>
 * {@code BaseEntity}를 상속받아 {@code id}, {@code createdAt}, {@code updatedAt} 필드를 기본으로 가집니다.
 *
 * <p><b>주요 생성자:</b><br>
 * {@code GithubCredential(...)} <br>
 * 롬복의 {@code @Builder}를 통해 생성되며, 연동에 필요한 필수 정보(Workspace ID, Display Name, Token, 등록자 ID)를 매개변수로 받습니다. <br>
 *
 * @author minhee
 * @see back.global.security.crypto.TinkCryptoConverter
 * @since 2026-04-30
 */
@Entity
@Table(name = "github_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubCredential extends BaseEntity {

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Convert(converter = TinkCryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT", name = "token_encrypted")
    private String token;

    @Column(nullable = false)
    private Long createdByMemberId;

    @Builder
    public GithubCredential(Long workspaceId, String displayName, String token, Long createdByMemberId) {
        this.workspaceId = workspaceId;
        this.displayName = displayName;
        this.token = token;
        this.createdByMemberId = createdByMemberId;
    }
}