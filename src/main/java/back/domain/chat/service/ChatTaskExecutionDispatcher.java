package back.domain.chat.service;

import back.domain.task.service.TaskRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTaskExecutionDispatcher {

    private final TaskRunService taskRunService;

    @Async("taskExecutionTaskExecutor")
    public void run(Long workspaceId, Long taskId, boolean createPr) {
        try {
            taskRunService.runTask(workspaceId, taskId, createPr);
        } catch (RuntimeException exception) {
            log.warn(
                    "Chat task execution failed. workspaceId={}, taskId={}",
                    workspaceId,
                    taskId,
                    exception);
        }
    }
}
