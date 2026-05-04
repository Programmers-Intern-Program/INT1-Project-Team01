package back.domain.task.controller;

import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.service.TaskService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/tasks")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskCreateResponse> createTask(
            @PathVariable Long workspaceId,
            @RequestBody TaskCreateRequest request
    ) {
        return ResponseEntity.ok(taskService.createTask(workspaceId, request));
    }

    @GetMapping
    public ResponseEntity<Page<TaskListResponse>> getTasks(
            @PathVariable Long workspaceId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(taskService.getTasks(workspaceId, pageable));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskDetailResponse> getTask(
            @PathVariable Long workspaceId,
            @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(taskService.getTask(workspaceId, taskId));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskStatusUpdateResponse> updateStatus(
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @RequestBody TaskStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(taskService.updateStatus(workspaceId, taskId, request));
    }
}
