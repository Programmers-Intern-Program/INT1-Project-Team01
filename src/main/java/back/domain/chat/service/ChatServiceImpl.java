package back.domain.chat.service;

import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.task.dto.response.TaskMessageResponse;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.service.TaskRunService;
import back.domain.task.service.TaskService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final TaskRunService taskRunService;
    private final TaskService taskService;
    private final ChatTaskExecutionDispatcher chatTaskExecutionDispatcher;

    @Override
    public ChatMessageSendResponse sendMessage(Long workspaceId, ChatMessageSendRequest request) {
        TaskRunResponse runResponse = taskRunService.createTaskForRun(workspaceId, request.toTaskRunRequest());
        chatTaskExecutionDispatcher.run(workspaceId, runResponse.taskId(), request.shouldCreatePr());
        List<TaskMessageResponse> messages = taskService.getTaskMessages(workspaceId, runResponse.taskId());
        return ChatMessageSendResponse.from(runResponse, messages);
    }

    @Override
    public List<TaskMessageResponse> getMessages(Long workspaceId, Long taskId) {
        return taskService.getTaskMessages(workspaceId, taskId);
    }
}
