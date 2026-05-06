package back.domain.gateway.service;

import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;

public interface WorkspaceGatewayBindingService {

    WorkspaceGatewayBindingRes bindExternalGateway(Long workspaceId, Long memberId, WorkspaceGatewayBindingReq request);

    OpenClawGatewayConnectionContext getConnectionContext(Long workspaceId);
}
