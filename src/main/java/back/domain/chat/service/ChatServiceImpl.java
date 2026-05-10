package back.domain.chat.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.request.SlackChatMessageSendCommand;
import back.domain.chat.dto.response.ChatMessageResponse;
import back.domain.chat.dto.response.ChatMessagesResponse;
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
import back.domain.orchestrator.dto.request.OrchestrationPlanCreateCommand;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.service.OrchestrationPlanService;
import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.dto.response.TaskMessageResponse;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import back.domain.task.service.TaskService;
import back.domain.task.service.TaskRunService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String GATEWAY_FAILURE_MESSAGE = "Agent 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";
    private static final int TASK_TITLE_MAX_LENGTH = 100;
    private static final int TASK_DESCRIPTION_MAX_LENGTH = 1000;
    private static final int SOURCE_ID_MAX_LENGTH = 255;
    private static final int OPEN_CLAW_SESSION_KEY_MAX_LENGTH = 220;
    private static final int SLACK_SESSION_HASH_LENGTH = 16;
    private static final int DEFAULT_POLL_LIMIT = 50;
    private static final int MAX_POLL_LIMIT = 100;
    private static final int ORCHESTRATOR_CONTEXT_AGENT_LIMIT = 20;

    private final TransactionOperations transactionOperations;
    private final TaskService taskService;
    private final TaskRunService taskRunService;
    private final ChatTaskExecutionDispatcher chatTaskExecutionDispatcher;
    private final ChatAgentIntentParser chatAgentIntentParser;
    private final OrchestrationPlanService orchestrationPlanService;
    private final AgentRepository agentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Override
    public ChatMessageSendResponse sendMessage(Long workspaceId, ChatMessageSendRequest request) {
        return sendMessageInternal(workspaceId, ChatSendCommand.from(request));
    }

    @Override
    public ChatMessageSendResponse sendSlackMessage(Long workspaceId, SlackChatMessageSendCommand command) {
        ChatSendCommand chatCommand = ChatSendCommand.from(command);
        if (chatCommand.agentName() == null) {
            return sendMessageInternal(workspaceId, chatCommand);
        }
        return sendSlackMessageWithNamedAgent(workspaceId, chatCommand);
    }

    private ChatMessageSendResponse sendSlackMessageWithNamedAgent(Long workspaceId, ChatSendCommand command) {
        Agent agent = agentRepository.findByWorkspaceIdAndName(workspaceId, command.agentName())
                .orElse(null);
        if (agent == null) {
            return slackGuidanceResponse(
                    workspaceId,
                    "요청한 Agent를 찾을 수 없습니다. /agent " + command.agentName() + " 이름을 확인해 주세요.");
        }

        String unavailableMessage = resolveSlackNamedAgentUnavailableMessage(agent);
        if (unavailableMessage != null) {
            return slackGuidanceResponse(workspaceId, unavailableMessage);
        }

        ChatSession existingSourceSession = findSourceSession(workspaceId, command);
        if (existingSourceSession != null && !existingSourceSession.getAgentId().equals(agent.getId())) {
            return slackGuidanceResponse(
                    workspaceId,
                    "이미 이 Slack thread는 다른 Agent와 연결되어 있습니다. 새 thread에서 다시 요청해 주세요.");
        }

        return sendMessageInternal(
                workspaceId,
                command.withAgentId(agent.getId()),
                ChatSendPrefetch.withNamedAgent(agent, existingSourceSession));
    }

    private String resolveSlackNamedAgentUnavailableMessage(Agent agent) {
        if (agent.getStatus() != AgentStatus.READY) {
            return "요청한 Agent가 READY 상태가 아닙니다. 현재 상태: " + agent.getStatus();
        }
        if (agent.getOpenClawAgentId() == null || agent.getOpenClawAgentId().isBlank()) {
            return "요청한 Agent가 OpenClaw Agent와 동기화되지 않았습니다.";
        }
        return null;
    }

    private ChatMessageSendResponse slackGuidanceResponse(Long workspaceId, String message) {
        return new ChatMessageSendResponse(
                null,
                null,
                workspaceId,
                null,
                null,
                null,
                null,
                message,
                null,
                null,
                List.of());
    }

    private ChatMessageSendResponse sendMessageInternal(Long workspaceId, ChatSendCommand command) {
        return sendMessageInternal(workspaceId, command, ChatSendPrefetch.empty());
    }

    private ChatMessageSendResponse sendMessageInternal(
            Long workspaceId,
            ChatSendCommand command,
            ChatSendPrefetch prefetch) {
        ChatSendContext context = createChatSendContextInTransaction(workspaceId, command, prefetch);
        logSlackChatPrepared(context);

        OpenClawChatResult chatResult;
        try {
            chatResult = sendOpenClawChat(
                    context.session(),
                    context.agent(),
                    context.normalizedMessage(),
                    context.userMessage().getId());
            logSlackChatSucceeded(context, chatResult);
        } catch (RuntimeException exception) {
            logSlackChatFailed(context, exception);
            recordFailureMessageSafely(context.session(), exception);
            throw exception;
        }

        ChatAgentIntent agentIntent = chatAgentIntentParser.parse(chatResult.finalText());
        ChatSendResult sendResult = requireTransactionResult(
                transactionOperations.execute(
                        status -> recordAgentResponse(context, command, agentIntent, chatResult.finalText())));
        sendResult.dispatch(chatTaskExecutionDispatcher);
        return sendResult.response();
    }

    private ChatSendContext createChatSendContextInTransaction(
            Long workspaceId,
            ChatSendCommand command,
            ChatSendPrefetch prefetch) {
        try {
            return requireTransactionResult(
                    transactionOperations.execute(status -> createChatSendContext(workspaceId, command, prefetch)));
        } catch (DataIntegrityViolationException exception) {
            if (command.source() != ChatSessionSource.SLACK) {
                throw exception;
            }
            log.info(
                    "Concurrent Slack ChatSession creation detected. "
                            + "Retry with existing session. workspaceId={}, sourceRef={}",
                    workspaceId,
                    command.sourceRef());
            return requireTransactionResult(
                    transactionOperations.execute(status ->
                            createChatSendContext(workspaceId, command, prefetch.retrySourceSessionLookup())));
        }
    }

    private ChatSendContext createChatSendContext(
            Long workspaceId,
            ChatSendCommand command,
            ChatSendPrefetch prefetch) {
        ChatSession existingSourceSession = resolvePrefetchedSourceSession(workspaceId, command, prefetch);
        Agent agent = resolvePrefetchedAgent(workspaceId, command, existingSourceSession, prefetch);
        ChatSession session = resolveSession(workspaceId, command, agent, existingSourceSession);
        String normalizedMessage = command.message().trim();
        ChatMessage userMessage = chatMessageRepository.save(
                ChatMessage.user(workspaceId, session.getId(), normalizedMessage));
        session.recordMessage();
        ChatSession savedSession = chatSessionRepository.save(session);
        return new ChatSendContext(agent, savedSession, userMessage, normalizedMessage);
    }

    private ChatSession resolvePrefetchedSourceSession(
            Long workspaceId,
            ChatSendCommand command,
            ChatSendPrefetch prefetch) {
        if (prefetch.sourceSessionResolved()) {
            return prefetch.sourceSession();
        }
        return findSourceSession(workspaceId, command);
    }

    private Agent resolvePrefetchedAgent(
            Long workspaceId,
            ChatSendCommand command,
            ChatSession existingSourceSession,
            ChatSendPrefetch prefetch) {
        if (prefetch.agent() == null) {
            return resolveAgent(workspaceId, command, existingSourceSession);
        }
        validateAgentReady(prefetch.agent());
        return prefetch.agent();
    }

    private ChatSendResult recordAgentResponse(
            ChatSendContext context,
            ChatSendCommand command,
            ChatAgentIntent agentIntent,
            String rawAgentResponse) {
        if (agentIntent.isTask()) {
            return recordTaskIntentResponse(context, command, agentIntent);
        }
        if (agentIntent.isOrchestration()) {
            return recordOrchestrationIntentResponse(context, agentIntent, rawAgentResponse);
        }
        return ChatSendResult.withoutDispatch(recordChatIntentResponse(context, agentIntent.message()));
    }

    private ChatMessageSendResponse recordChatIntentResponse(ChatSendContext context, String assistantMessage) {
        ChatSession session = context.session();
        ChatMessage savedAssistantMessage = chatMessageRepository.save(
                ChatMessage.assistant(session.getWorkspaceId(), session.getId(), assistantMessage));
        session.recordMessage();
        ChatSession savedSession = chatSessionRepository.save(session);

        return new ChatMessageSendResponse(
                savedSession.getId(),
                null,
                savedSession.getWorkspaceId(),
                context.agent().getId(),
                null,
                null,
                null,
                assistantMessage,
                null,
                savedSession.getCreatedAt(),
                List.of(
                        ChatMessageResponse.from(context.userMessage()),
                        ChatMessageResponse.from(savedAssistantMessage)));
    }

    private ChatSendResult recordTaskIntentResponse(
            ChatSendContext context,
            ChatSendCommand command,
            ChatAgentIntent agentIntent) {
        ChatSession session = context.session();
        TaskRunRequest taskRequest = createTaskRunRequest(context, command, agentIntent.task());
        TaskRunResponse taskResponse = taskRunService.createTaskForRun(session.getWorkspaceId(), taskRequest);
        ChatMessage linkedUserMessage = linkUserMessageToTask(context.userMessage().getId(), taskResponse.taskId());

        ChatMessage assistantMessage =
                ChatMessage.assistant(session.getWorkspaceId(), session.getId(), agentIntent.message());
        assistantMessage.linkTask(taskResponse.taskId(), null);
        ChatMessage savedAssistantMessage = chatMessageRepository.save(assistantMessage);
        session.recordMessage();
        ChatSession savedSession = chatSessionRepository.save(session);

        ChatMessageSendResponse response = new ChatMessageSendResponse(
                savedSession.getId(),
                taskResponse.taskId(),
                savedSession.getWorkspaceId(),
                context.agent().getId(),
                taskResponse.taskStatus(),
                taskResponse.taskExecutionId(),
                taskResponse.executionStatus(),
                agentIntent.message(),
                taskResponse.failureReason(),
                savedSession.getCreatedAt(),
                List.of(
                        ChatMessageResponse.from(linkedUserMessage),
                        ChatMessageResponse.from(savedAssistantMessage)));
        return ChatSendResult.withDispatch(
                response,
                new ChatTaskDispatch(
                        savedSession.getWorkspaceId(),
                        taskResponse.taskId(),
                        taskRequest.shouldCreatePr(),
                        savedSession.getOpenClawSessionKey()));
    }

    private ChatSendResult recordOrchestrationIntentResponse(
            ChatSendContext context,
            ChatAgentIntent agentIntent,
            String rawAgentResponse) {
        if (context.agent().getCategory() != AgentCategory.ORCHESTRATOR) {
            return ChatSendResult.withoutDispatch(
                    recordChatIntentResponse(context, "Orchestrator Agent만 작업 계획을 생성할 수 있습니다."));
        }

        OrchestrationPlan plan;
        try {
            plan = orchestrationPlanService.createPlan(createOrchestrationPlanCreateCommand(
                    context,
                    agentIntent.orchestrationPlan(),
                    rawAgentResponse));
        } catch (ServiceException exception) {
            log.warn(
                    "Invalid Orchestrator plan response. workspaceId={}, chatSessionId={}, agentId={}, reason={}",
                    context.session().getWorkspaceId(),
                    context.session().getId(),
                    context.agent().getId(),
                    exception.getLogMessage());
            return ChatSendResult.withoutDispatch(recordChatIntentResponse(context, exception.getClientMessage()));
        }

        ChatSession session = context.session();
        ChatMessage savedAssistantMessage = chatMessageRepository.save(
                ChatMessage.assistant(session.getWorkspaceId(), session.getId(), agentIntent.message()));
        plan.linkAssistantMessage(savedAssistantMessage.getId());
        session.recordMessage();
        ChatSession savedSession = chatSessionRepository.save(session);

        return ChatSendResult.withoutDispatch(new ChatMessageSendResponse(
                savedSession.getId(),
                null,
                savedSession.getWorkspaceId(),
                context.agent().getId(),
                null,
                null,
                null,
                agentIntent.message(),
                null,
                savedSession.getCreatedAt(),
                List.of(
                        ChatMessageResponse.from(context.userMessage()),
                        ChatMessageResponse.from(savedAssistantMessage))));
    }

    private OrchestrationPlanCreateCommand createOrchestrationPlanCreateCommand(
            ChatSendContext context,
            ChatAgentIntent.OrchestrationPlanSpec planSpec,
            String rawAgentResponse) {
        return new OrchestrationPlanCreateCommand(
                context.session().getWorkspaceId(),
                context.session().getId(),
                context.agent().getId(),
                context.userMessage().getId(),
                planSpec.title(),
                rawAgentResponse,
                planSpec.steps()
                        .stream()
                        .map(this::toOrchestrationStepCommand)
                        .toList());
    }

    private OrchestrationPlanCreateCommand.StepCommand toOrchestrationStepCommand(
            ChatAgentIntent.OrchestrationStepSpec stepSpec) {
        return new OrchestrationPlanCreateCommand.StepCommand(
                stepSpec.stepKey(),
                stepSpec.agentId(),
                stepSpec.agentName(),
                stepSpec.category(),
                stepSpec.title(),
                stepSpec.prompt(),
                stepSpec.dependsOn());
    }

    private ChatMessage linkUserMessageToTask(Long userMessageId, Long taskId) {
        ChatMessage userMessage = chatMessageRepository.findById(userMessageId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[ChatServiceImpl#linkUserMessageToTask] user chat message not found. userMessageId="
                                + userMessageId,
                        "채팅 메시지를 찾을 수 없습니다."));
        userMessage.linkTask(taskId, null);
        return chatMessageRepository.save(userMessage);
    }

    private TaskRunRequest createTaskRunRequest(
            ChatSendContext context,
            ChatSendCommand command,
            ChatAgentIntent.TaskSpec taskSpec) {
        return new TaskRunRequest(
                resolveTaskTitle(context, command, taskSpec),
                resolveTaskDescription(context, taskSpec),
                resolveTaskType(command, taskSpec),
                resolveTaskPriority(command, taskSpec),
                context.agent().getId(),
                resolveRepositoryId(command, taskSpec),
                resolveSourceType(command),
                resolveSourceId(context, command),
                context.normalizedMessage(),
                resolveCreatePr(command, taskSpec));
    }

    private String resolveTaskTitle(
            ChatSendContext context,
            ChatSendCommand command,
            ChatAgentIntent.TaskSpec taskSpec) {
        String title = firstText(taskSpec.title(), command.title(), context.normalizedMessage());
        return truncate(title, TASK_TITLE_MAX_LENGTH);
    }

    private String resolveTaskDescription(ChatSendContext context, ChatAgentIntent.TaskSpec taskSpec) {
        return truncate(firstText(taskSpec.description(), context.normalizedMessage()), TASK_DESCRIPTION_MAX_LENGTH);
    }

    private TaskType resolveTaskType(ChatSendCommand command, ChatAgentIntent.TaskSpec taskSpec) {
        if (taskSpec.taskType() != null) {
            return taskSpec.taskType();
        }
        if (command.taskType() != null) {
            return command.taskType();
        }
        return TaskType.OTHER;
    }

    private TaskPriority resolveTaskPriority(ChatSendCommand command, ChatAgentIntent.TaskSpec taskSpec) {
        if (taskSpec.priority() != null) {
            return taskSpec.priority();
        }
        if (command.priority() != null) {
            return command.priority();
        }
        return TaskPriority.MEDIUM;
    }

    private Long resolveRepositoryId(ChatSendCommand command, ChatAgentIntent.TaskSpec taskSpec) {
        if (taskSpec.repositoryId() != null) {
            return taskSpec.repositoryId();
        }
        return command.repositoryId();
    }

    private SourceType resolveSourceType(ChatSendCommand command) {
        if (command.source() == ChatSessionSource.SLACK) {
            return SourceType.SLACK;
        }
        return SourceType.DASHBOARD;
    }

    private String resolveSourceId(ChatSendContext context, ChatSendCommand command) {
        if (command.sourceRef() != null && !command.sourceRef().isBlank()) {
            return truncate(command.sourceRef(), SOURCE_ID_MAX_LENGTH);
        }
        return truncate("chat-session-" + context.session().getId(), SOURCE_ID_MAX_LENGTH);
    }

    private Boolean resolveCreatePr(ChatSendCommand command, ChatAgentIntent.TaskSpec taskSpec) {
        if (taskSpec.createPr() != null) {
            return taskSpec.createPr();
        }
        return command.createPr();
    }

    @Override
    public ChatMessagesResponse getSessionMessages(
            Long workspaceId, Long chatSessionId, Long afterMessageId, Integer limit) {
        Long normalizedAfterMessageId = normalizeAfterMessageId(afterMessageId);
        int normalizedLimit = normalizePollLimit(limit);
        ChatSession session = chatSessionRepository.findByIdAndWorkspaceId(chatSessionId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[ChatServiceImpl#getSessionMessages] chat session not found. workspaceId="
                                + workspaceId + ", chatSessionId=" + chatSessionId,
                        "채팅 세션을 찾을 수 없습니다."));

        List<ChatMessage> messages = findSessionMessages(
                workspaceId,
                session.getId(),
                normalizedAfterMessageId,
                normalizedLimit,
                limit);
        boolean hasMore = isLimitedPoll(normalizedAfterMessageId, limit) && messages.size() > normalizedLimit;
        List<ChatMessageResponse> responseMessages = messages.stream()
                .limit(hasMore ? normalizedLimit : messages.size())
                .map(ChatMessageResponse::from)
                .toList();
        return new ChatMessagesResponse(
                session.getId(),
                responseMessages,
                resolveNextCursor(responseMessages, normalizedAfterMessageId),
                hasMore);
    }

    private List<ChatMessage> findSessionMessages(
            Long workspaceId, Long chatSessionId, Long afterMessageId, int normalizedLimit, Integer rawLimit) {
        if (!isLimitedPoll(afterMessageId, rawLimit)) {
            return chatMessageRepository
                    .findByWorkspaceIdAndChatSessionIdOrderByCreatedAtAscIdAsc(workspaceId, chatSessionId);
        }
        Long cursor = afterMessageId;
        if (cursor == null) {
            cursor = 0L;
        }
        return chatMessageRepository
                .findByWorkspaceIdAndChatSessionIdAndIdGreaterThanOrderByCreatedAtAscIdAsc(
                        workspaceId,
                        chatSessionId,
                        cursor,
                        PageRequest.of(0, normalizedLimit + 1));
    }

    private boolean isLimitedPoll(Long afterMessageId, Integer limit) {
        return afterMessageId != null || limit != null;
    }

    private Long normalizeAfterMessageId(Long afterMessageId) {
        if (afterMessageId == null) {
            return null;
        }
        if (afterMessageId < 0) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[ChatServiceImpl#normalizeAfterMessageId] afterMessageId is negative: " + afterMessageId,
                    "afterMessageId는 0 이상이어야 합니다.");
        }
        return afterMessageId;
    }

    private int normalizePollLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_POLL_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[ChatServiceImpl#normalizePollLimit] limit is not positive: " + limit,
                    "limit은 1 이상이어야 합니다.");
        }
        return Math.min(limit, MAX_POLL_LIMIT);
    }

    private Long resolveNextCursor(List<ChatMessageResponse> messages, Long afterMessageId) {
        return messages.stream()
                .map(ChatMessageResponse::messageId)
                .filter(Objects::nonNull)
                .reduce((previous, current) -> current)
                .orElse(afterMessageId);
    }

    @Override
    public List<TaskMessageResponse> getMessages(Long workspaceId, Long taskId) {
        return taskService.getTaskMessages(workspaceId, taskId);
    }

    private Agent resolveAgent(Long workspaceId, ChatSendCommand command, ChatSession existingSourceSession) {
        Long agentId = resolveAgentId(command, existingSourceSession);
        if (agentId == null) {
            if (command.source() == ChatSessionSource.WEB) {
                throw new ServiceException(
                        CommonErrorCode.BAD_REQUEST,
                        "[ChatServiceImpl#resolveAgent] agentId is required for web chat. workspaceId=" + workspaceId,
                        "agentId는 필수입니다.");
            }
            Agent agent = agentRepository
                    .findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                            workspaceId, AgentStatus.READY)
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[ChatServiceImpl#resolveAgent] ready agent not found. workspaceId=" + workspaceId,
                            "실행 가능한 READY Agent가 없습니다."));
            validateAgentReady(agent);
            return agent;
        }
        Agent agent = agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[ChatServiceImpl#resolveAgent] agent not found. workspaceId="
                                + workspaceId + ", agentId=" + agentId,
                        "선택한 Agent를 찾을 수 없습니다."));
        validateAgentReady(agent);
        return agent;
    }

    private Long resolveAgentId(ChatSendCommand command, ChatSession existingSourceSession) {
        if (command.agentId() != null) {
            return command.agentId();
        }
        if (existingSourceSession != null) {
            return existingSourceSession.getAgentId();
        }
        return null;
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

    private ChatSession findSourceSession(Long workspaceId, ChatSendCommand command) {
        if (command.source() != ChatSessionSource.SLACK) {
            return null;
        }
        return chatSessionRepository
                .findByWorkspaceIdAndSourceAndSourceRef(workspaceId, ChatSessionSource.SLACK, command.sourceRef())
                .orElse(null);
    }

    private ChatSession resolveSession(
            Long workspaceId,
            ChatSendCommand command,
            Agent agent,
            ChatSession existingSourceSession) {
        if (command.chatSessionId() != null) {
            ChatSession session = chatSessionRepository.findByIdAndWorkspaceId(command.chatSessionId(), workspaceId)
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[ChatServiceImpl#resolveSession] chat session not found. workspaceId="
                                    + workspaceId + ", chatSessionId=" + command.chatSessionId(),
                            "채팅 세션을 찾을 수 없습니다."));
            validateReusableSession(session, agent);
            return session;
        }
        if (existingSourceSession != null) {
            validateReusableSession(existingSourceSession, agent);
            return ensureSlackOpenClawSessionKey(existingSourceSession, agent);
        }
        if (command.source() == ChatSessionSource.SLACK) {
            return chatSessionRepository.save(ChatSession.start(
                    workspaceId,
                    agent.getId(),
                    ChatSessionSource.SLACK,
                    command.sourceRef(),
                    createSlackOpenClawSessionKey(workspaceId, agent.getId(), command.sourceRef())));
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
                    buildAgentIntentMessage(session.getWorkspaceId(), agent, message),
                    createIdempotencyKey(session.getId(), userMessageId)));
        } finally {
            client.close();
        }
    }

    private String buildAgentIntentMessage(Long workspaceId, Agent agent, String message) {
        return String.join(
                System.lineSeparator(),
                "You are connected to AI Office chat.",
                buildOrchestratorContext(workspaceId, agent),
                "Decide whether the user needs general chat, a single executable task, or a multi-agent plan.",
                "Return only one JSON object.",
                "CHAT response: {\"intent\":\"CHAT\",\"message\":\"general answer\"}",
                "TASK response: {\"intent\":\"TASK\",\"message\":\"task accepted\",\"task\":{\"title\":\"task title\","
                        + "\"description\":\"task detail\",\"taskType\":\"OTHER\",\"priority\":\"MEDIUM\","
                        + "\"repositoryId\":null,\"createPr\":false}}",
                "Allowed taskType values: CODE_REVIEW, PR_REVIEW, BUG_FIX, FEATURE_IMPLEMENTATION, REFACTORING,"
                        + " TEST_CREATION, DOCUMENTATION, PR_CREATION, OTHER.",
                "Allowed priority values: LOW, MEDIUM, HIGH, URGENT.",
                buildOrchestrationIntentGuide(agent),
                "User message:",
                message);
    }

    private String buildOrchestrationIntentGuide(Agent agent) {
        if (agent.getCategory() != AgentCategory.ORCHESTRATOR) {
            return "ORCHESTRATE response is only allowed for ORCHESTRATOR agents.";
        }
        return "ORCHESTRATE response for multi-agent plans: "
                + "{\"intent\":\"ORCHESTRATE\",\"message\":\"plan accepted\","
                + "\"plan\":{\"title\":\"plan title\",\"steps\":[{\"stepKey\":\"backend-1\","
                + "\"agentId\":2,\"agentName\":\"backend-agent\",\"category\":\"BACKEND\","
                + "\"title\":\"step title\",\"prompt\":\"worker instruction\",\"dependsOn\":[]}]}}";
    }

    private String buildOrchestratorContext(Long workspaceId, Agent agent) {
        if (agent.getCategory() != AgentCategory.ORCHESTRATOR) {
            return "Current Agent category: " + agent.getCategory();
        }
        List<Agent> readyAgents = agentRepository
                .findByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        workspaceId, AgentStatus.READY);
        return String.join(
                System.lineSeparator(),
                "Current Agent category: ORCHESTRATOR",
                "Available READY agents in this workspace: "
                        + readyAgents.size()
                        + " total, showing "
                        + Math.min(readyAgents.size(), ORCHESTRATOR_CONTEXT_AGENT_LIMIT)
                        + ".",
                formatReadyAgents(readyAgents, ORCHESTRATOR_CONTEXT_AGENT_LIMIT),
                "Use only listed agentId values when planning work. Do not invent agents.");
    }

    private String formatReadyAgents(List<Agent> readyAgents, int limit) {
        if (readyAgents.isEmpty()) {
            return "- none";
        }
        String formattedAgents = readyAgents.stream()
                .limit(limit)
                .map(agent -> "- agentId=" + agent.getId()
                        + ", name=" + agent.getName()
                        + ", category=" + agent.getCategory()
                        + ", status=" + agent.getStatus()
                        + ", openClawAgentId=" + agent.getOpenClawAgentId())
                .collect(Collectors.joining(System.lineSeparator()));
        if (readyAgents.size() <= limit) {
            return formattedAgents;
        }
        return formattedAgents
                + System.lineSeparator()
                + "- ... "
                + (readyAgents.size() - limit)
                + " more READY agents omitted.";
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

    private String createSlackOpenClawSessionKey(Long workspaceId, Long agentId, String sourceRef) {
        String sourceRefHash = HashUtils.sha256Hex(sourceRef).substring(0, SLACK_SESSION_HASH_LENGTH);
        return "workspace-" + workspaceId + "-agent-" + agentId + "-slack-" + sourceRefHash;
    }

    private ChatSession ensureSlackOpenClawSessionKey(ChatSession session, Agent agent) {
        if (session.getSource() != ChatSessionSource.SLACK) {
            return session;
        }

        String expectedSessionKey =
                createSlackOpenClawSessionKey(session.getWorkspaceId(), agent.getId(), session.getSourceRef());
        if (expectedSessionKey.equals(session.getOpenClawSessionKey())) {
            return session;
        }

        log.info(
                "Normalize Slack ChatSession OpenClaw session key. "
                        + "workspaceId={}, chatSessionId={}, agentId={}, sourceRefHash={}, previousKeyLength={}",
                session.getWorkspaceId(),
                session.getId(),
                agent.getId(),
                sourceRefHash(session.getSourceRef()),
                session.getOpenClawSessionKey().length());
        session.replaceOpenClawSessionKey(expectedSessionKey);
        return session;
    }

    private void logSlackChatPrepared(ChatSendContext context) {
        ChatSession session = context.session();
        if (session.getSource() != ChatSessionSource.SLACK || !log.isInfoEnabled()) {
            return;
        }
        log.info(
                "Slack Agent chat prepared. workspaceId={}, chatSessionId={}, sourceRefHash={}, "
                        + "agentId={}, openClawAgentId={}, openClawSessionKey={}, userMessageId={}, messageLength={}",
                session.getWorkspaceId(),
                session.getId(),
                sourceRefHash(session.getSourceRef()),
                context.agent().getId(),
                context.agent().getOpenClawAgentId(),
                session.getOpenClawSessionKey(),
                context.userMessage().getId(),
                context.normalizedMessage().length());
    }

    private void logSlackChatSucceeded(ChatSendContext context, OpenClawChatResult chatResult) {
        ChatSession session = context.session();
        if (session.getSource() != ChatSessionSource.SLACK || !log.isInfoEnabled()) {
            return;
        }
        log.info(
                "Slack Agent chat succeeded. workspaceId={}, chatSessionId={}, sourceRefHash={}, "
                        + "agentId={}, openClawAgentId={}, finalTextLength={}",
                session.getWorkspaceId(),
                session.getId(),
                sourceRefHash(session.getSourceRef()),
                context.agent().getId(),
                context.agent().getOpenClawAgentId(),
                chatResult.finalText() == null ? 0 : chatResult.finalText().length());
    }

    private void logSlackChatFailed(ChatSendContext context, RuntimeException exception) {
        ChatSession session = context.session();
        if (session.getSource() != ChatSessionSource.SLACK || !log.isWarnEnabled()) {
            return;
        }
        log.warn(
                "Slack Agent chat failed. workspaceId={}, chatSessionId={}, sourceRefHash={}, "
                        + "agentId={}, openClawAgentId={}, openClawSessionKey={}, exceptionType={}, message={}",
                session.getWorkspaceId(),
                session.getId(),
                sourceRefHash(session.getSourceRef()),
                context.agent().getId(),
                context.agent().getOpenClawAgentId(),
                session.getOpenClawSessionKey(),
                exception.getClass().getSimpleName(),
                exception.getMessage());
    }

    private String sourceRefHash(String sourceRef) {
        return HashUtils.sha256Hex(sourceRef).substring(0, SLACK_SESSION_HASH_LENGTH);
    }

    private String createIdempotencyKey(Long chatSessionId, Long userMessageId) {
        return "chat-session-" + chatSessionId + "-message-" + userMessageId;
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second.trim();
    }

    private String firstText(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return firstText(second, third);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private <T> T requireTransactionResult(T result) {
        return Objects.requireNonNull(result, "transaction result must not be null");
    }

    private record ChatSendCommand(
            String message,
            Long agentId,
            String agentName,
            Long repositoryId,
            TaskType taskType,
            TaskPriority priority,
            String title,
            Boolean createPr,
            Long chatSessionId,
            ChatSessionSource source,
            String sourceRef) {

        private static ChatSendCommand from(ChatMessageSendRequest request) {
            return new ChatSendCommand(
                    request.message(),
                    request.agentId(),
                    null,
                    request.repositoryId(),
                    request.taskType(),
                    request.priority(),
                    request.title(),
                    request.createPr(),
                    request.chatSessionId(),
                    ChatSessionSource.WEB,
                    null);
        }

        private static ChatSendCommand from(SlackChatMessageSendCommand command) {
            return new ChatSendCommand(
                    command.message(),
                    command.agentId(),
                    normalizeOptionalText(command.agentName()),
                    command.repositoryId(),
                    command.taskType(),
                    command.priority(),
                    command.title(),
                    command.createPr(),
                    null,
                    ChatSessionSource.SLACK,
                    requireSourceRef(command.sourceRef()));
        }

        private ChatSendCommand withAgentId(Long resolvedAgentId) {
            return new ChatSendCommand(
                    message,
                    resolvedAgentId,
                    agentName,
                    repositoryId,
                    taskType,
                    priority,
                    title,
                    createPr,
                    chatSessionId,
                    source,
                    sourceRef);
        }

        private static String requireSourceRef(String sourceRef) {
            if (sourceRef == null || sourceRef.isBlank()) {
                throw new ServiceException(
                        CommonErrorCode.BAD_REQUEST,
                        "[ChatServiceImpl.ChatSendCommand#requireSourceRef] slack sourceRef is blank",
                        "Slack thread 참조값은 필수입니다.");
            }
            return sourceRef.trim();
        }

        private static String normalizeOptionalText(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }
    }

    private record ChatSendPrefetch(
            Agent agent,
            ChatSession sourceSession,
            boolean sourceSessionResolved) {

        private static ChatSendPrefetch empty() {
            return new ChatSendPrefetch(null, null, false);
        }

        private static ChatSendPrefetch withNamedAgent(Agent agent, ChatSession sourceSession) {
            return new ChatSendPrefetch(agent, sourceSession, true);
        }

        private ChatSendPrefetch retrySourceSessionLookup() {
            return new ChatSendPrefetch(agent, null, false);
        }
    }

    private record ChatSendContext(
            Agent agent,
            ChatSession session,
            ChatMessage userMessage,
            String normalizedMessage) {}

    private record ChatSendResult(ChatMessageSendResponse response, ChatTaskDispatch dispatch) {

        private static ChatSendResult withoutDispatch(ChatMessageSendResponse response) {
            return new ChatSendResult(response, null);
        }

        private static ChatSendResult withDispatch(ChatMessageSendResponse response, ChatTaskDispatch dispatch) {
            return new ChatSendResult(response, dispatch);
        }

        private void dispatch(ChatTaskExecutionDispatcher dispatcher) {
            if (dispatch == null) {
                return;
            }
            try {
                dispatcher.run(
                        dispatch.workspaceId(),
                        dispatch.taskId(),
                        dispatch.createPr(),
                        dispatch.openClawSessionKeyOverride());
            } catch (RuntimeException exception) {
                log.warn(
                        "Failed to dispatch chat task execution. workspaceId={}, taskId={}",
                        dispatch.workspaceId(),
                        dispatch.taskId(),
                        exception);
            }
        }
    }

    private record ChatTaskDispatch(
            Long workspaceId,
            Long taskId,
            boolean createPr,
            String openClawSessionKeyOverride) {}
}
