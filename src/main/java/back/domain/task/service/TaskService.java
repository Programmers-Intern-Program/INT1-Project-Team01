package back.domain.task.service;

import back.domain.task.domain.Task;
import back.domain.task.domain.TaskStatus;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional
    public TaskCreateResponse createTask(Long workspaceId, TaskCreateRequest request) {
        Task task = Task.create(
                workspaceId,
                request.title(),
                request.description(),
                request.taskType(),
                request.priority(),
                request.assignedAgentId(),
                request.repositoryId(),
                request.sourceType(),
                request.sourceId(),
                request.originalRequest()
        );

        Task savedTask = taskRepository.save(task);

        return TaskCreateResponse.from(savedTask);
    }

    public Page<TaskListResponse> getTasks(Long workspaceId, Pageable pageable) {
        return taskRepository.findByWorkspaceId(workspaceId, pageable)
                .map(TaskListResponse::from);
    }

    public TaskDetailResponse getTask(Long workspaceId, Long taskId) {
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Task를 찾을 수 없습니다."));

        return TaskDetailResponse.from(task);
    }

    @Transactional
    public TaskStatusUpdateResponse updateStatus(
            Long workspaceId,
            Long taskId,
            TaskStatusUpdateRequest request
    ) {
        Task task = taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Task를 찾을 수 없습니다."));

        TaskStatus previousStatus = task.getStatus();

        task.updateStatus(request.status());

        return new TaskStatusUpdateResponse(
                task.getId(),
                previousStatus,
                task.getStatus(),
                task.getUpdatedAt()
        );
    }
}
