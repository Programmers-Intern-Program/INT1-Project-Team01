package back.domain.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.gateway.client.OpenClawAgentSummary;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.request.WorkspaceGatewayConnectionTestReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingStatus;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.dto.response.WorkspaceGatewayConnectionTestRes;
import back.domain.gateway.dto.response.WorkspaceGatewayStatusRes;
import back.domain.gateway.entity.GatewayConnectionStatus;
import back.domain.gateway.entity.GatewayMode;
import back.domain.gateway.entity.WorkspaceGatewayBinding;
import back.domain.gateway.exception.OpenClawGatewayErrorCode;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.repository.WorkspaceGatewayBindingRepository;
import back.domain.member.entity.Member;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class WorkspaceGatewayBindingServiceImplTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceGatewayBindingRepository workspaceGatewayBindingRepository;

    @Mock
    private OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Mock
    private OpenClawGatewayClient openClawGatewayClient;

    @InjectMocks
    private WorkspaceGatewayBindingServiceImpl workspaceGatewayBindingService;

    private Member member;
    private Workspace workspace;
    private WorkspaceMember admin;

    @BeforeEach
    void setUp() {
        member = Member.createUser("sub", "test@test.com", "관리자");
        ReflectionTestUtils.setField(member, "id", 10L);

        workspace = Workspace.create("AI Office", "설명", member);
        ReflectionTestUtils.setField(workspace, "id", 1L);

        admin = WorkspaceMember.create(workspace, member, WorkspaceMemberRole.ADMIN);
    }

    @Test
    @DisplayName("Gateway 상태 조회는 binding이 없으면 UNBOUND 상태를 반환한다")
    void getWorkspaceGatewayStatus_missingBinding_returnsUnbound() {
        // given
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.empty());

        // when
        WorkspaceGatewayStatusRes response = workspaceGatewayBindingService.getWorkspaceGatewayStatus(1L, 10L);

        // then
        assertThat(response.status()).isEqualTo(WorkspaceGatewayBindingStatus.UNBOUND);
        assertThat(response.bound()).isFalse();
        assertThat(response.maskedToken()).isNull();
    }

    @Test
    @DisplayName("Gateway 상태 조회는 binding과 마지막 연결 상태를 반환하고 token을 masking한다")
    void getWorkspaceGatewayStatus_existingBinding_returnsBoundStatus() {
        // given
        WorkspaceGatewayBinding binding =
                WorkspaceGatewayBinding.external(workspace, "ws://localhost:34115", "gateway-secret-token", 10L);
        ReflectionTestUtils.setField(binding, "id", 100L);
        binding.recordConnectionTestResult(GatewayConnectionStatus.CONNECTED, null);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.of(binding));

        // when
        WorkspaceGatewayStatusRes response = workspaceGatewayBindingService.getWorkspaceGatewayStatus(1L, 10L);

        // then
        assertThat(response.status()).isEqualTo(WorkspaceGatewayBindingStatus.BOUND);
        assertThat(response.bound()).isTrue();
        assertThat(response.bindingId()).isEqualTo(100L);
        assertThat(response.gatewayUrl()).isEqualTo("ws://localhost:34115");
        assertThat(response.maskedToken()).isEqualTo("gate****oken");
        assertThat(response.lastStatus()).isEqualTo(GatewayConnectionStatus.CONNECTED);
        assertThat(response.lastCheckedAt()).isNotNull();
        assertThat(response.lastError()).isNull();
    }

    @Test
    @DisplayName("워크스페이스 관리자는 외부 Gateway URL과 token을 binding으로 저장할 수 있다")
    void bindExternalGateway_newBinding_success() {
        // given
        WorkspaceGatewayBindingReq request =
                new WorkspaceGatewayBindingReq("http://localhost:34115", "gateway-secret-token");
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.empty());
        given(workspaceGatewayBindingRepository.save(any(WorkspaceGatewayBinding.class)))
                .willAnswer(invocation -> {
                    WorkspaceGatewayBinding binding = invocation.getArgument(0);
                    ReflectionTestUtils.setField(binding, "id", 100L);
                    return binding;
                });

        // when
        WorkspaceGatewayBindingRes response = workspaceGatewayBindingService.bindExternalGateway(1L, 10L, request);

        // then
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.workspaceId()).isEqualTo(1L);
        assertThat(response.mode()).isEqualTo(GatewayMode.EXTERNAL);
        assertThat(response.gatewayUrl()).isEqualTo("ws://localhost:34115");
        assertThat(response.maskedToken()).isEqualTo("gate****oken");
        verify(workspaceGatewayBindingRepository).save(any(WorkspaceGatewayBinding.class));
    }

    @Test
    @DisplayName("기존 binding이 있으면 같은 row의 Gateway URL과 token을 갱신한다")
    void bindExternalGateway_existingBinding_updates() {
        // given
        WorkspaceGatewayBinding binding =
                WorkspaceGatewayBinding.external(workspace, "ws://localhost:1111", "old-secret-token", 10L);
        ReflectionTestUtils.setField(binding, "id", 100L);
        binding.recordConnectionTestResult(GatewayConnectionStatus.CONNECTED, null);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.of(binding));
        given(workspaceGatewayBindingRepository.save(binding)).willReturn(binding);

        // when
        WorkspaceGatewayBindingRes response = workspaceGatewayBindingService.bindExternalGateway(
                1L, 10L, new WorkspaceGatewayBindingReq("https://gateway.example.com/ws", "new-secret-token"));

        // then
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.gatewayUrl()).isEqualTo("wss://gateway.example.com/ws");
        assertThat(binding.getToken()).isEqualTo("new-secret-token");
        assertThat(binding.getLastStatus()).isNull();
        assertThat(binding.getLastCheckedAt()).isNull();
        assertThat(binding.getLastError()).isNull();
    }

    @Test
    @DisplayName("짧은 token은 응답에서 전체 masking한다")
    void bindExternalGateway_shortToken_masksAll() {
        // given
        WorkspaceGatewayBindingReq request = new WorkspaceGatewayBindingReq("ws://localhost:34115", "123456789");
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.empty());
        given(workspaceGatewayBindingRepository.save(any(WorkspaceGatewayBinding.class)))
                .willAnswer(invocation -> {
                    WorkspaceGatewayBinding binding = invocation.getArgument(0);
                    ReflectionTestUtils.setField(binding, "id", 100L);
                    return binding;
                });

        // when
        WorkspaceGatewayBindingRes response = workspaceGatewayBindingService.bindExternalGateway(1L, 10L, request);

        // then
        assertThat(response.maskedToken()).isEqualTo("****");
    }

    @Test
    @DisplayName("Gateway binding 저장 시 연결 검증을 요청하면 연결 성공 결과를 같이 저장한다")
    void bindExternalGateway_validateConnection_success() {
        // given
        WorkspaceGatewayBindingReq request =
                new WorkspaceGatewayBindingReq("https://gateway.example.com", "gateway-secret-token", true);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.listAgents()).willReturn(List.of(new OpenClawAgentSummary("agent-1", "backend")));
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.empty());
        given(workspaceGatewayBindingRepository.save(any(WorkspaceGatewayBinding.class)))
                .willAnswer(invocation -> {
                    WorkspaceGatewayBinding binding = invocation.getArgument(0);
                    ReflectionTestUtils.setField(binding, "id", 100L);
                    return binding;
                });

        // when
        WorkspaceGatewayBindingRes response = workspaceGatewayBindingService.bindExternalGateway(1L, 10L, request);

        // then
        assertThat(response.id()).isEqualTo(100L);
        ArgumentCaptor<OpenClawGatewayConnectionContext> contextCaptor =
                ArgumentCaptor.forClass(OpenClawGatewayConnectionContext.class);
        verify(openClawGatewayClient).connect(contextCaptor.capture());
        assertThat(contextCaptor.getValue().gatewayUrl()).isEqualTo("wss://gateway.example.com");
        assertThat(contextCaptor.getValue().token()).isEqualTo("gateway-secret-token");
        ArgumentCaptor<WorkspaceGatewayBinding> bindingCaptor =
                ArgumentCaptor.forClass(WorkspaceGatewayBinding.class);
        verify(workspaceGatewayBindingRepository).save(bindingCaptor.capture());
        assertThat(bindingCaptor.getValue().getLastStatus()).isEqualTo(GatewayConnectionStatus.CONNECTED);
        assertThat(bindingCaptor.getValue().getLastError()).isNull();
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Gateway binding 저장 시 연결 검증에 실패하면 binding을 저장하지 않고 원인을 반환한다")
    void bindExternalGateway_validateConnectionFailed_throwsException() {
        // given
        WorkspaceGatewayBindingReq request =
                new WorkspaceGatewayBindingReq("ws://localhost:34115", "gateway-secret-token", true);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        OpenClawGatewayException exception = new OpenClawGatewayException(
                OpenClawGatewayErrorCode.UNAUTHORIZED,
                "TOKEN_INVALID",
                "token=gateway-secret-token is invalid",
                OpenClawGatewayErrorCode.UNAUTHORIZED.defaultMessage(),
                null,
                false,
                false);
        willThrow(exception).given(openClawGatewayClient).connect(any(OpenClawGatewayConnectionContext.class));

        // when & then
        assertThatThrownBy(() -> workspaceGatewayBindingService.bindExternalGateway(1L, 10L, request))
                .isInstanceOf(ServiceException.class)
                .extracting("clientMessage")
                .isEqualTo("Gateway token이 올바르지 않습니다. OpenClaw에서 발급된 Gateway token을 다시 확인해 주세요.");
        verify(workspaceGatewayBindingRepository, never()).findByWorkspaceId(1L);
        verify(workspaceGatewayBindingRepository, never()).save(any(WorkspaceGatewayBinding.class));
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Gateway URL에 host가 없으면 사용자 친화 메시지로 거부한다")
    void bindExternalGateway_missingHost_throwsException() {
        // given
        WorkspaceGatewayBindingReq request =
                new WorkspaceGatewayBindingReq("https:///gateway", "gateway-secret-token");
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));

        // when & then
        assertThatThrownBy(() -> workspaceGatewayBindingService.bindExternalGateway(1L, 10L, request))
                .isInstanceOf(ServiceException.class)
                .extracting("clientMessage")
                .isEqualTo("Gateway URL에 호스트가 포함되어야 합니다. 예: https://xxxx.ngrok-free.app");
        verify(openClawGatewayClientFactory, never()).create();
        verify(workspaceGatewayBindingRepository, never()).save(any(WorkspaceGatewayBinding.class));
    }

    @Test
    @DisplayName("Gateway 연결 테스트는 URL을 WebSocket URL로 정규화하고 agents.list 성공 상태를 반환한다")
    void testExternalGateway_success() {
        // given
        WorkspaceGatewayConnectionTestReq request =
                new WorkspaceGatewayConnectionTestReq("https://gateway.example.com", "gateway-secret-token");
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.listAgents()).willReturn(List.of(
                new OpenClawAgentSummary("agent-1", "backend"),
                new OpenClawAgentSummary("agent-2", "frontend")));
        WorkspaceGatewayBinding binding =
                WorkspaceGatewayBinding.external(workspace, "wss://gateway.example.com", "gateway-secret-token", 10L);
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.of(binding));

        // when
        WorkspaceGatewayConnectionTestRes response = workspaceGatewayBindingService.testExternalGateway(
                1L, 10L, request);

        // then
        assertThat(response.status()).isEqualTo(GatewayConnectionStatus.CONNECTED);
        assertThat(response.connected()).isTrue();
        assertThat(response.gatewayUrl()).isEqualTo("wss://gateway.example.com");
        assertThat(response.agentCount()).isEqualTo(2);
        ArgumentCaptor<OpenClawGatewayConnectionContext> contextCaptor =
                ArgumentCaptor.forClass(OpenClawGatewayConnectionContext.class);
        verify(openClawGatewayClient).connect(contextCaptor.capture());
        assertThat(contextCaptor.getValue().gatewayUrl()).isEqualTo("wss://gateway.example.com");
        assertThat(contextCaptor.getValue().token()).isEqualTo("gateway-secret-token");
        assertThat(binding.getLastStatus()).isEqualTo(GatewayConnectionStatus.CONNECTED);
        assertThat(binding.getLastCheckedAt()).isNotNull();
        assertThat(binding.getLastError()).isNull();
        verify(workspaceGatewayBindingRepository).save(binding);
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Gateway 연결 테스트는 인증 실패를 TOKEN_INVALID 상태로 반환한다")
    void testExternalGateway_tokenInvalid_returnsStatus() {
        // given
        WorkspaceGatewayConnectionTestReq request =
                new WorkspaceGatewayConnectionTestReq("ws://localhost:34115", "gateway-secret-token");
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        WorkspaceGatewayBinding binding =
                WorkspaceGatewayBinding.external(workspace, "ws://localhost:34115", "gateway-secret-token", 10L);
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.of(binding));
        OpenClawGatewayException exception = new OpenClawGatewayException(
                OpenClawGatewayErrorCode.UNAUTHORIZED,
                "TOKEN_INVALID",
                "token=gateway-secret-token is invalid",
                OpenClawGatewayErrorCode.UNAUTHORIZED.defaultMessage(),
                null,
                false,
                false);
        willThrow(exception).given(openClawGatewayClient).connect(any(OpenClawGatewayConnectionContext.class));

        // when
        WorkspaceGatewayConnectionTestRes response = workspaceGatewayBindingService.testExternalGateway(
                1L, 10L, request);

        // then
        assertThat(response.status()).isEqualTo(GatewayConnectionStatus.TOKEN_INVALID);
        assertThat(response.connected()).isFalse();
        assertThat(response.message()).doesNotContain("gateway-secret-token");
        assertThat(response.message()).contains("Gateway token");
        assertThat(binding.getLastStatus()).isEqualTo(GatewayConnectionStatus.TOKEN_INVALID);
        assertThat(binding.getLastError()).isEqualTo(response.message());
        verify(workspaceGatewayBindingRepository).save(binding);
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Gateway 연결 테스트는 RPC timeout을 TIMEOUT 상태로 반환한다")
    void testExternalGateway_timeout_returnsStatus() {
        // given
        WorkspaceGatewayConnectionTestReq request =
                new WorkspaceGatewayConnectionTestReq("ws://localhost:34115", "gateway-secret-token");
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(admin));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        WorkspaceGatewayBinding binding =
                WorkspaceGatewayBinding.external(workspace, "ws://localhost:34115", "gateway-secret-token", 10L);
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.of(binding));
        given(openClawGatewayClient.listAgents())
                .willThrow(OpenClawGatewayException.rpcTimeout("agents.list", "request-1"));

        // when
        WorkspaceGatewayConnectionTestRes response = workspaceGatewayBindingService.testExternalGateway(
                1L, 10L, request);

        // then
        assertThat(response.status()).isEqualTo(GatewayConnectionStatus.TIMEOUT);
        assertThat(response.connected()).isFalse();
        assertThat(response.message()).contains("응답 시간이 초과");
        assertThat(binding.getLastStatus()).isEqualTo(GatewayConnectionStatus.TIMEOUT);
        assertThat(binding.getLastError()).isEqualTo(response.message());
        verify(workspaceGatewayBindingRepository).save(binding);
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("binding 조회는 Gateway connection context를 반환하고 token을 toString에 노출하지 않는다")
    void getConnectionContext_existingBinding_success() {
        // given
        WorkspaceGatewayBinding binding =
                WorkspaceGatewayBinding.external(workspace, "ws://localhost:34115", "gateway-secret-token", 10L);
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.of(binding));

        // when
        OpenClawGatewayConnectionContext context = workspaceGatewayBindingService.getConnectionContext(1L);

        // then
        assertThat(context.gatewayUrl()).isEqualTo("ws://localhost:34115");
        assertThat(context.token()).isEqualTo("gateway-secret-token");
        assertThat(context.toString()).doesNotContain("gateway-secret-token");
    }

    @Test
    @DisplayName("binding이 없으면 ServiceException이 발생한다")
    void getConnectionContext_missingBinding_throwsException() {
        // given
        given(workspaceGatewayBindingRepository.findByWorkspaceId(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> workspaceGatewayBindingService.getConnectionContext(1L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("워크스페이스 관리자가 아니면 Gateway binding을 저장할 수 없다")
    void bindExternalGateway_notAdmin_throwsException() {
        // given
        WorkspaceMember normalMember = WorkspaceMember.create(workspace, member, WorkspaceMemberRole.MEMBER);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 10L)).willReturn(Optional.of(normalMember));

        // when & then
        assertThatThrownBy(() -> workspaceGatewayBindingService.bindExternalGateway(
                        1L, 10L, new WorkspaceGatewayBindingReq("ws://localhost:34115", "gateway-secret-token")))
                .isInstanceOf(ServiceException.class);
    }
}
