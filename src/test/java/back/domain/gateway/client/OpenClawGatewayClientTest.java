package back.domain.gateway.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawGatewayClientTest {

    @Test
    @DisplayName("Gateway client는 connect와 agents.list 계약을 제공한다")
    void clientContract_requiredMethods_success() throws NoSuchMethodException {
        // when
        Method connect = OpenClawGatewayClient.class.getMethod(
                "connect",
                OpenClawGatewayConnectionContext.class
        );
        Method listAgents = OpenClawGatewayClient.class.getMethod("listAgents");

        // then
        assertThat(connect.getReturnType()).isEqualTo(Void.TYPE);
        assertThat(listAgents.getReturnType()).isEqualTo(List.class);
    }
}
