package back.domain.gateway.service;

import java.net.URI;
import java.util.List;
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
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.repository.WorkspaceGatewayBindingRepository;
import back.domain.workspace.entity.Workspace;
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WorkspaceGatewayBindingRes bindExternalGateway(
            Long workspaceId, Long memberId, WorkspaceGatewayBindingReq request) {
        Workspace workspace = requireAdmin(workspaceId, memberId);
        String normalizedGatewayUrl = normalizeGatewayUrl(request.gatewayUrl());
        WorkspaceGatewayConnectionTestRes validationResult =
                validateConnectionIfRequested(request, normalizedGatewayUrl);

        WorkspaceGatewayBinding binding = workspaceGatewayBindingRepository
                .findByWorkspaceId(workspaceId)
                .map(existing -> {
                    existing.updateExternal(normalizedGatewayUrl, request.token(), memberId);
                    recordValidationResult(existing, validationResult);
                    return existing;
                })
                .orElseGet(() -> {
                    WorkspaceGatewayBinding newBinding = WorkspaceGatewayBinding.external(
                            workspace, normalizedGatewayUrl, request.token(), memberId);
                    recordValidationResult(newBinding, validationResult);
                    return newBinding;
                });

        binding = workspaceGatewayBindingRepository.save(binding);
        return WorkspaceGatewayBindingRes.from(binding);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WorkspaceGatewayConnectionTestRes testExternalGateway(
            Long workspaceId, Long memberId, WorkspaceGatewayConnectionTestReq request) {
        requireAdmin(workspaceId, memberId);
        String normalizedGatewayUrl = normalizeGatewayUrl(request.gatewayUrl());
        WorkspaceGatewayConnectionTestRes response = checkExternalGateway(normalizedGatewayUrl, request.token());
        recordConnectionTestIfCurrentBinding(workspaceId, normalizedGatewayUrl, request.token(), response);
        return response;
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

    private WorkspaceGatewayConnectionTestRes validateConnectionIfRequested(
            WorkspaceGatewayBindingReq request, String normalizedGatewayUrl) {
        if (!request.validateConnection()) {
            return null;
        }
        WorkspaceGatewayConnectionTestRes response = checkExternalGateway(normalizedGatewayUrl, request.token());
        if (!response.connected()) {
            throw new ServiceException(
                    GatewayConnectionFailureResolver.resolveErrorCode(response.status()),
                    "[WorkspaceGatewayBindingServiceImpl#bindExternalGateway] gateway validation failed. status="
                            + response.status(),
                    response.message());
        }
        return response;
    }

    private WorkspaceGatewayConnectionTestRes checkExternalGateway(String normalizedGatewayUrl, String token) {
        OpenClawGatewayClient client = openClawGatewayClientFactory.create();

        try {
            client.connect(new OpenClawGatewayConnectionContext(normalizedGatewayUrl, token));
            List<OpenClawAgentSummary> agents = client.listAgents();
            return WorkspaceGatewayConnectionTestRes.connected(normalizedGatewayUrl, agents.size());
        } catch (OpenClawGatewayException exception) {
            GatewayConnectionStatus status = GatewayConnectionFailureResolver.resolveStatus(exception);
            WorkspaceGatewayConnectionTestRes response = WorkspaceGatewayConnectionTestRes.failed(
                    status, normalizedGatewayUrl, GatewayConnectionFailureResolver.resolveClientMessage(status));
            return response;
        } catch (RuntimeException exception) {
            return WorkspaceGatewayConnectionTestRes.failed(
                    GatewayConnectionStatus.UNREACHABLE,
                    normalizedGatewayUrl,
                    GatewayConnectionFailureResolver.resolveClientMessage(GatewayConnectionStatus.UNREACHABLE));
        } finally {
            client.close();
        }
    }

    private void recordValidationResult(
            WorkspaceGatewayBinding binding, WorkspaceGatewayConnectionTestRes validationResult) {
        if (validationResult == null) {
            return;
        }
        binding.recordConnectionTestResult(validationResult.status(), null);
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

    private Workspace requireAdmin(Long workspaceId, Long memberId) {
        Workspace workspace = workspaceRepository
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
        return workspace;
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
                    resolveGatewayUrlClientMessage(exception));
        }
    }

    private String resolveGatewayUrlClientMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if ("gatewayUrl must not be blank".equals(message)) {
            return "Gateway URL은 필수입니다.";
        }
        if ("gatewayUrl scheme is required".equals(message)) {
            return "Gateway URL은 http://, https://, ws://, wss:// 로 시작해야 합니다.";
        }
        if ("gatewayUrl host is required".equals(message)) {
            return "Gateway URL에 호스트가 포함되어야 합니다. 예: https://xxxx.ngrok-free.app";
        }
        if ("gatewayUrl fragment is not supported".equals(message)) {
            return "Gateway URL에는 #fragment를 포함할 수 없습니다.";
        }
        if (message != null && message.startsWith("unsupported gatewayUrl scheme")) {
            return "Gateway URL은 http, https, ws, wss scheme만 사용할 수 있습니다.";
        }
        return "Gateway URL 형식이 올바르지 않습니다. 예: https://xxxx.ngrok-free.app";
    }
}
