package back.domain.gateway.client;

import java.util.List;

public interface OpenClawGatewayClient {

    void connect(OpenClawGatewayConnectionContext context);

    List<OpenClawAgentSummary> listAgents();

    OpenClawAgentSummary createAgent(OpenClawAgentCreateCommand command);

    void setAgentFile(OpenClawAgentFileCommand command);

    OpenClawChatResult sendChat(OpenClawChatCommand command);

    void close();
}
