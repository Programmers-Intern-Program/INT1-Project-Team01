package back.domain.chat.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.response.ChatMessageResponse;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.entity.ChatMessage;
import back.domain.chat.entity.ChatSession;
import back.domain.chat.entity.ChatSessionSource;
import back.domain.chat.entity.ChatSessionStatus;
import back.domain.chat.repository.ChatMessageRepository;
import back.domain.chat.repository.ChatSessionRepository;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.task.dto.response.TaskMessageResponse;
import back.domain.task.service.TaskService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String GATEWAY_FAILURE_MESSAGE = "Agent 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private final TransactionOperations transactionOperations;
    private final TaskService taskService;
    private final AgentRepository agentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Override
    public ChatMessageSendResponse sendMessage(Long workspaceId, ChatMessageSendRequest request) {
        ChatSendContext context = requireTransactionResult(
                transactionOperations.execute(status -> createChatSendContext(workspaceId, request)));

        OpenClawChatResult chatResult;
        try {
            chatResult = sendOpenClawChat(
                    context.session(),
                    context.agent(),
                    context.normalizedMessage(),
                    context.userMessage().getId());
        } catch (RuntimeException exception) {
            recordFailureMessageSafely(context.session(), exception);
            throw exception;
        }

        return requireTransactionResult(
                transactionOperations.execute(status -> recordAssistantResponse(context, chatResult)));
    }

    private ChatSendContext createChatSendContext(Long workspaceId, ChatMessageSendRequest request) {
        Agent agent = resolveAgent(workspaceId, request.agentId());
        ChatSession session = resolveSession(workspaceId, request, agent);
        String normalizedMessage = request.message().trim();
        ChatMessage userMessage = chatMessageRepository.save(
                ChatMessage.user(workspaceId, session.getId(), normalizedMessage));
        session.recordMessage();
        ChatSession savedSession = chatSessionRepository.save(session);
        return new ChatSendContext(agent, savedSession, userMessage, normalizedMessage);
    }

    private ChatMessageSendResponse recordAssistantResponse(ChatSendContext context, OpenClawChatResult chatResult) {
        ChatSession session = context.session();
        chatMessageRepository.save(
                ChatMessage.assistant(session.getWorkspaceId(), session.getId(), chatResult.finalText()));
        session.recordMessage();
        ChatSession savedSession = chatSessionRepository.save(session);

        List<ChatMessageResponse> messages = getSessionMessages(savedSession.getWorkspaceId(), savedSession.getId());
        return new ChatMessageSendResponse(
                savedSession.getId(),
                null,
                savedSession.getWorkspaceId(),
                context.agent().getId(),
                null,
                null,
                null,
                chatResult.finalText(),
                null,
                savedSession.getCreatedAt(),
                messages);
    }

    @Override
    public List<ChatMessageResponse> getSessionMessages(Long workspaceId, Long chatSessionId) {
        ChatSession session = chatSessionRepository.findByIdAndWorkspaceId(chatSessionId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[ChatServiceImpl#getSessionMessages] chat session not found. workspaceId="
                                + workspaceId + ", chatSessionId=" + chatSessionId,
                        "채팅 세션을 찾을 수 없습니다."));
        return chatMessageRepository
                .findByWorkspaceIdAndChatSessionIdOrderByCreatedAtAscIdAsc(workspaceId, session.getId())
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Override
    public List<TaskMessageResponse> getMessages(Long workspaceId, Long taskId) {
        return taskService.getTaskMessages(workspaceId, taskId);
    }

    private Agent resolveAgent(Long workspaceId, Long agentId) {
        Agent agent = agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[ChatServiceImpl#resolveAgent] agent not found. workspaceId="
                                + workspaceId + ", agentId=" + agentId,
                        "선택한 Agent를 찾을 수 없습니다."));
        validateAgentReady(agent);
        return agent;
    }

    private void validateAgentReady(Agent agent) {
        if (agent.getStatus() != AgentStatus.READY) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST_STATE,
                    "[ChatServiceImpl#validateAgentReady] agent is not READY. agentId="
                            + agent.getId() + ", status=" + agent.getStatus(),
                    "선택한 Agent가 READY 상태가 아닙니다.");
        }
        if (agent.getOpenClawAgentId() == null || agent.getOpenClawAgentId().isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST_STATE,
                    "[ChatServiceImpl#validateAgentReady] agent has no OpenClaw agent id. agentId=" + agent.getId(),
                    "선택한 Agent가 OpenClaw Agent와 동기화되지 않았습니다.");
        }
    }

    private ChatSession resolveSession(Long workspaceId, ChatMessageSendRequest request, Agent agent) {
        if (request.chatSessionId() != null) {
            ChatSession session = chatSessionRepository.findByIdAndWorkspaceId(request.chatSessionId(), workspaceId)
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[ChatServiceImpl#resolveSession] chat session not found. workspaceId="
                                    + workspaceId + ", chatSessionId=" + request.chatSessionId(),
                            "채팅 세션을 찾을 수 없습니다."));
            validateReusableSession(session, agent);
            return session;
        }
        return chatSessionRepository.save(ChatSession.start(
                workspaceId,
                agent.getId(),
                ChatSessionSource.WEB,
                null,
                createWebOpenClawSessionKey(workspaceId, agent.getId())));
    }

    private void validateReusableSession(ChatSession session, Agent agent) {
        if (!session.getAgentId().equals(agent.getId())) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST_STATE,
                    "[ChatServiceImpl#validateReusableSession] session agent mismatch. chatSessionId="
                            + session.getId() + ", sessionAgentId=" + session.getAgentId()
                            + ", requestAgentId=" + agent.getId(),
                    "채팅 세션의 Agent와 요청 Agent가 다릅니다.");
        }
        if (session.getStatus() != ChatSessionStatus.ACTIVE) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST_STATE,
                    "[ChatServiceImpl#validateReusableSession] chat session is not ACTIVE. chatSessionId="
                            + session.getId() + ", status=" + session.getStatus(),
                    "활성 상태가 아닌 채팅 세션입니다.");
        }
    }

    private OpenClawChatResult sendOpenClawChat(
            ChatSession session, Agent agent, String message, Long userMessageId) {
        OpenClawGatewayConnectionContext context =
                workspaceGatewayBindingService.getConnectionContext(session.getWorkspaceId());
        OpenClawGatewayClient client = openClawGatewayClientFactory.create();
        try {
            client.connect(context);
            return client.sendChat(new OpenClawChatCommand(
                    agent.getOpenClawAgentId(),
                    session.getOpenClawSessionKey(),
                    message,
                    createIdempotencyKey(session.getId(), userMessageId)));
        } finally {
            client.close();
        }
    }

    private void recordFailureMessageSafely(ChatSession session, RuntimeException exception) {
        try {
            recordFailureMessage(session, resolveFailureMessage(exception));
        } catch (RuntimeException recordException) {
            exception.addSuppressed(recordException);
        }
    }

    private void recordFailureMessage(ChatSession session, String failureMessage) {
        requireTransactionResult(transactionOperations.execute(status -> {
            ChatSession currentSession = chatSessionRepository
                    .findByIdAndWorkspaceId(session.getId(), session.getWorkspaceId())
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[ChatServiceImpl#recordFailureMessage] chat session not found. workspaceId="
                                    + session.getWorkspaceId() + ", chatSessionId=" + session.getId(),
                            "채팅 세션을 찾을 수 없습니다."));
            chatMessageRepository.save(
                    ChatMessage.system(currentSession.getWorkspaceId(), currentSession.getId(), failureMessage));
            currentSession.recordMessage();
            chatSessionRepository.save(currentSession);
            return currentSession.getId();
        }));
    }

    private String resolveFailureMessage(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException) {
            String clientMessage = serviceException.getClientMessage();
            if (clientMessage != null && !clientMessage.isBlank()) {
                return clientMessage;
            }
        }
        return GATEWAY_FAILURE_MESSAGE;
    }

    private String createWebOpenClawSessionKey(Long workspaceId, Long agentId) {
        return "workspace-" + workspaceId + "-agent-" + agentId + "-chat-" + UUID.randomUUID();
    }

    private String createIdempotencyKey(Long chatSessionId, Long userMessageId) {
        return "chat-session-" + chatSessionId + "-message-" + userMessageId;
    }

    private <T> T requireTransactionResult(T result) {
        return Objects.requireNonNull(result, "transaction result must not be null");
    }

    private record ChatSendContext(
            Agent agent,
            ChatSession session,
            ChatMessage userMessage,
            String normalizedMessage) {}
}
