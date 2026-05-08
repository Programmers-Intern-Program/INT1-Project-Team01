package back.domain.chat.service;

import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.task.dto.response.TaskMessageResponse;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.service.TaskRunService;
import back.domain.task.service.TaskService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final TaskRunService taskRunService;
    private final TaskService taskService;
    private final ChatTaskExecutionDispatcher chatTaskExecutionDispatcher;

    @Override
    public ChatMessageSendResponse sendMessage(Long workspaceId, ChatMessageSendRequest request) {
        TaskRunResponse runResponse = taskRunService.createTaskForRun(workspaceId, request.toTaskRunRequest());
        dispatchTaskExecution(workspaceId, runResponse.taskId(), request.shouldCreatePr());
        List<TaskMessageResponse> messages = taskService.getTaskMessages(workspaceId, runResponse.taskId());
        return ChatMessageSendResponse.from(runResponse, messages);
    }

    @Override
    public List<TaskMessageResponse> getMessages(Long workspaceId, Long taskId) {
        return taskService.getTaskMessages(workspaceId, taskId);
    }

    private void dispatchTaskExecution(Long workspaceId, Long taskId, boolean createPr) {
        try {
            chatTaskExecutionDispatcher.run(workspaceId, taskId, createPr);
        } catch (RuntimeException exception) {
            markTaskFailedBestEffort(workspaceId, taskId, exception);
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "Agent 실행 요청 등록에 실패했습니다. workspaceId="
                            + workspaceId
                            + ", taskId="
                            + taskId,
                    "Agent 실행을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void markTaskFailedBestEffort(Long workspaceId, Long taskId, RuntimeException originalException) {
        try {
            taskRunService.markTaskFailed(workspaceId, taskId);
        } catch (RuntimeException statusException) {
            log.warn(
                    "Failed to mark task as FAILED after dispatch rejection. workspaceId={}, taskId={}",
                    workspaceId,
                    taskId,
                    statusException);
            originalException.addSuppressed(statusException);
        }
    }
}
