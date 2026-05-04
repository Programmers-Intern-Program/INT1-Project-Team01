package back.domain.github.entity;

import back.domain.workspace.entity.Workspace;
import back.global.jpa.entity.BaseEntity;
import back.global.security.crypto.TinkCryptoConverter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.*;
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
 * @author minhee
 * @see back.global.security.crypto.TinkCryptoConverter
 * @since 2026-04-30
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "JPA 연관 엔티티 반환은 Spring 컨텍스트에서 관리됨")
@Entity
@Table(name = "github_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Convert(converter = TinkCryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT", name = "token_encrypted")
    private String token;

    @Column(nullable = false)
    private Long createdByMemberId;

    @Builder
    public GithubCredential(Workspace workspace, String displayName, String token, Long createdByMemberId) {
        this.workspace = workspace;
        this.displayName = displayName;
        this.token = token;
        this.createdByMemberId = createdByMemberId;
    }
}