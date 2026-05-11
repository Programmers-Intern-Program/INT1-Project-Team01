package back.domain.gateway.service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import back.domain.gateway.client.OpenClawAgentSummary;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.client.transport.GatewayUrlNormalizer;
import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.request.WorkspaceGatewayConnectionTestReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.dto.response.WorkspaceGatewayConnectionTestRes;
import back.domain.gateway.dto.response.WorkspaceGatewayStatusRes;
import back.domain.gateway.entity.GatewayConnectionStatus;
import back.domain.gateway.entity.WorkspaceGatewayBinding;
import back.domain.gateway.exception.OpenClawGatewayErrorCode;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.repository.WorkspaceGatewayBindingRepository;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceGatewayBindingServiceImpl implements WorkspaceGatewayBindingService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceGatewayBindingRepository workspaceGatewayBindingRepository;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;
    private final GatewayUrlNormalizer gatewayUrlNormalizer = new GatewayUrlNormalizer();

    @Override
    public WorkspaceGatewayStatusRes getWorkspaceGatewayStatus(Long workspaceId, Long memberId) {
        requireAdmin(workspaceId, memberId);
        return workspaceGatewayBindingRepository
                .findByWorkspaceId(workspaceId)
                .map(WorkspaceGatewayStatusRes::from)
                .orElseGet(WorkspaceGatewayStatusRes::unbound);
    }

    @Override
    @Transactional
    public WorkspaceGatewayBindingRes bindExternalGateway(
            Long workspaceId, Long memberId, WorkspaceGatewayBindingReq request) {
        WorkspaceMember admin = requireAdmin(workspaceId, memberId);
        String normalizedGatewayUrl = normalizeGatewayUrl(request.gatewayUrl());
        WorkspaceGatewayBinding binding = workspaceGatewayBindingRepository
                .findByWorkspaceId(workspaceId)
                .map(existing -> {
                    existing.updateExternal(normalizedGatewayUrl, request.token(), memberId);
                    return existing;
                })
                .orElseGet(() -> workspaceGatewayBindingRepository.save(WorkspaceGatewayBinding.external(
                        admin.getWorkspace(), normalizedGatewayUrl, request.token(), memberId)));

        return WorkspaceGatewayBindingRes.from(binding);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WorkspaceGatewayConnectionTestRes testExternalGateway(
            Long workspaceId, Long memberId, WorkspaceGatewayConnectionTestReq request) {
        requireAdmin(workspaceId, memberId);
        String normalizedGatewayUrl = normalizeGatewayUrl(request.gatewayUrl());
        OpenClawGatewayClient client = openClawGatewayClientFactory.create();

        try {
            client.connect(new OpenClawGatewayConnectionContext(normalizedGatewayUrl, request.token()));
            List<OpenClawAgentSummary> agents = client.listAgents();
            WorkspaceGatewayConnectionTestRes response =
                    WorkspaceGatewayConnectionTestRes.connected(normalizedGatewayUrl, agents.size());
            recordConnectionTestIfCurrentBinding(workspaceId, normalizedGatewayUrl, request.token(), response);
            return response;
        } catch (OpenClawGatewayException exception) {
            GatewayConnectionStatus status = resolveConnectionTestStatus(exception);
            WorkspaceGatewayConnectionTestRes response = WorkspaceGatewayConnectionTestRes.failed(
                    status, normalizedGatewayUrl, exception.getClientMessage());
            recordConnectionTestIfCurrentBinding(workspaceId, normalizedGatewayUrl, request.token(), response);
            return response;
        } catch (RuntimeException exception) {
            WorkspaceGatewayConnectionTestRes response = WorkspaceGatewayConnectionTestRes.failed(
                    GatewayConnectionStatus.UNREACHABLE,
                    normalizedGatewayUrl,
                    OpenClawGatewayErrorCode.CONNECTION_FAILED.defaultMessage());
            recordConnectionTestIfCurrentBinding(workspaceId, normalizedGatewayUrl, request.token(), response);
            return response;
        } finally {
            client.close();
        }
    }

    @Override
    public OpenClawGatewayConnectionContext getConnectionContext(Long workspaceId) {
        WorkspaceGatewayBinding binding = workspaceGatewayBindingRepository
                .findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[WorkspaceGatewayBindingServiceImpl#getConnectionContext] gateway binding not found",
                        "워크스페이스 Gateway 설정이 존재하지 않습니다."));
        return new OpenClawGatewayConnectionContext(binding.getGatewayUrl(), binding.getToken());
    }

    private void recordConnectionTestIfCurrentBinding(
            Long workspaceId, String gatewayUrl, String token, WorkspaceGatewayConnectionTestRes response) {
        workspaceGatewayBindingRepository
                .findByWorkspaceId(workspaceId)
                .filter(binding -> isSameBindingTarget(binding, gatewayUrl, token))
                .ifPresent(binding -> {
                    String errorMessage = response.connected() ? null : response.message();
                    binding.recordConnectionTestResult(response.status(), errorMessage);
                    workspaceGatewayBindingRepository.save(binding);
                });
    }

    private boolean isSameBindingTarget(WorkspaceGatewayBinding binding, String gatewayUrl, String token) {
        return Objects.equals(binding.getGatewayUrl(), gatewayUrl)
                && Objects.equals(binding.getToken(), token);
    }

    private GatewayConnectionStatus resolveConnectionTestStatus(OpenClawGatewayException exception) {
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.UNAUTHORIZED
                || matchesGatewayCode(exception.gatewayErrorCode(), "UNAUTHORIZED", "AUTH_FAILED", "TOKEN_INVALID")) {
            return GatewayConnectionStatus.TOKEN_INVALID;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.RPC_TIMEOUT
                || containsIgnoreCase(exception.gatewayErrorCode(), "timeout")) {
            return GatewayConnectionStatus.TIMEOUT;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.PAIRING_REQUIRED) {
            return GatewayConnectionStatus.PAIRING_REQUIRED;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.FORBIDDEN) {
            return GatewayConnectionStatus.FORBIDDEN;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.GATEWAY_DISCONNECTED
                || exception.getErrorCode() == OpenClawGatewayErrorCode.CONNECTION_FAILED
                || exception.getErrorCode() == OpenClawGatewayErrorCode.SEND_FAILED) {
            return GatewayConnectionStatus.UNREACHABLE;
        }
        return GatewayConnectionStatus.FAILED;
    }

    private boolean matchesGatewayCode(String gatewayErrorCode, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(gatewayErrorCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private WorkspaceMember requireAdmin(Long workspaceId, Long memberId) {
        workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[WorkspaceGatewayBindingServiceImpl#requireAdmin] workspace not found",
                        "워크스페이스가 존재하지 않습니다."));
        WorkspaceMember workspaceMember = workspaceMemberRepository
                .findByWorkspaceIdAndMemberId(workspaceId, memberId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.FORBIDDEN,
                        "[WorkspaceGatewayBindingServiceImpl#requireAdmin] workspace membership not found",
                        "워크스페이스 접근 권한이 없습니다."));
        if (workspaceMember.getRole() != WorkspaceMemberRole.ADMIN) {
            throw new ServiceException(
                    CommonErrorCode.FORBIDDEN,
                    "[WorkspaceGatewayBindingServiceImpl#requireAdmin] workspace member is not admin",
                    "워크스페이스 관리자 권한이 필요합니다.");
        }
        if (workspaceMember.getWorkspace() == null) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspaceGatewayBindingServiceImpl#requireAdmin] workspace member has no workspace",
                    "워크스페이스 정보를 확인하지 못했습니다.");
        }
        return workspaceMember;
    }

    private String normalizeGatewayUrl(String gatewayUrl) {
        try {
            URI webSocketUri = gatewayUrlNormalizer.toWebSocketUri(gatewayUrl);
            return webSocketUri.toString();
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[WorkspaceGatewayBindingServiceImpl#normalizeGatewayUrl] invalid gateway url: "
                            + exception.getMessage(),
                    "Gateway URL 형식이 올바르지 않습니다.");
        }
    }
}
