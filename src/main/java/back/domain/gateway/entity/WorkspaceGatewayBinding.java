package back.domain.gateway.entity;

import back.domain.workspace.entity.Workspace;
import back.global.jpa.entity.BaseEntity;
import back.global.security.crypto.TinkCryptoConverter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "CT_CONSTRUCTOR_THROW"},
        justification = "JPA 연관 엔티티 반환은 Spring 컨텍스트에서 관리되며, 생성자 예외는 도메인 불변식 보호를 위한 의도적 설계임")
@Getter
@Entity
@Table(name = "workspace_gateway_bindings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceGatewayBinding extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, unique = true)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GatewayMode mode;

    @Column(nullable = false, length = 2048)
    private String gatewayUrl;

    @Convert(converter = TinkCryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT", name = "token_encrypted")
    private String token;

    @Column(nullable = false)
    private Long updatedByMemberId;

    private WorkspaceGatewayBinding(
            Workspace workspace, GatewayMode mode, String gatewayUrl, String token, Long updatedByMemberId) {
        this.workspace = requireWorkspace(workspace);
        this.mode = mode == null ? GatewayMode.EXTERNAL : mode;
        this.gatewayUrl = requireNotBlank(gatewayUrl, "gatewayUrl");
        this.token = requireNotBlank(token, "token");
        this.updatedByMemberId = requireMemberId(updatedByMemberId);
    }

    public static WorkspaceGatewayBinding external(
            Workspace workspace, String gatewayUrl, String token, Long memberId) {
        return new WorkspaceGatewayBinding(workspace, GatewayMode.EXTERNAL, gatewayUrl, token, memberId);
    }

    public void updateExternal(String gatewayUrl, String token, Long memberId) {
        this.mode = GatewayMode.EXTERNAL;
        this.gatewayUrl = requireNotBlank(gatewayUrl, "gatewayUrl");
        this.token = requireNotBlank(token, "token");
        this.updatedByMemberId = requireMemberId(memberId);
    }

    private static Workspace requireWorkspace(Workspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        return workspace;
    }

    private static Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }
        return memberId;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
