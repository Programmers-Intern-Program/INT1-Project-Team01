package back.domain.gateway.service;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.request.WorkspaceGatewayConnectionTestReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.dto.response.WorkspaceGatewayConnectionTestRes;

public interface WorkspaceGatewayBindingService {

    WorkspaceGatewayBindingRes bindExternalGateway(Long workspaceId, Long memberId, WorkspaceGatewayBindingReq request);

    WorkspaceGatewayConnectionTestRes testExternalGateway(
            Long workspaceId, Long memberId, WorkspaceGatewayConnectionTestReq request);

    OpenClawGatewayConnectionContext getConnectionContext(Long workspaceId);
}
