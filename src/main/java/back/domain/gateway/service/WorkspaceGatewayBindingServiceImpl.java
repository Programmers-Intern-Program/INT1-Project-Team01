package back.domain.gateway.service;

import java.net.URI;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.client.transport.GatewayUrlNormalizer;
import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.entity.WorkspaceGatewayBinding;
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
    private final GatewayUrlNormalizer gatewayUrlNormalizer = new GatewayUrlNormalizer();

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
    public OpenClawGatewayConnectionContext getConnectionContext(Long workspaceId) {
        WorkspaceGatewayBinding binding = workspaceGatewayBindingRepository
                .findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[WorkspaceGatewayBindingServiceImpl#getConnectionContext] gateway binding not found",
                        "워크스페이스 Gateway 설정이 존재하지 않습니다."));
        return new OpenClawGatewayConnectionContext(binding.getGatewayUrl(), binding.getToken());
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
