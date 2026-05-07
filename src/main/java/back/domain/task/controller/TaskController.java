package back.domain.task.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.AgentReportResponse;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.service.TaskRunService;
import back.domain.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskRunService taskRunService;

    @PostMapping
    public ResponseEntity<TaskCreateResponse> createTask(
            @PathVariable Long workspaceId, @Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.ok(taskService.createTask(workspaceId, request));
    }

    @PostMapping("/run")
    public ResponseEntity<TaskRunResponse> createAndRunTask(
            @PathVariable Long workspaceId, @Valid @RequestBody TaskRunRequest request) {
        return ResponseEntity.ok(taskRunService.createAndRunTask(workspaceId, request));
    }

    @GetMapping
    public ResponseEntity<Page<TaskListResponse>> getTasks(@PathVariable Long workspaceId, Pageable pageable) {
        return ResponseEntity.ok(taskService.getTasks(workspaceId, pageable));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskDetailResponse> getTask(@PathVariable Long workspaceId, @PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getTask(workspaceId, taskId));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskStatusUpdateResponse> updateStatus(
            @PathVariable Long workspaceId, @PathVariable Long taskId, @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateStatus(workspaceId, taskId, request));
    }

    @GetMapping("/{taskId}/logs")
    public ResponseEntity<List<TaskLogResponse>> getTaskLogs(
            @PathVariable Long workspaceId, @PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getTaskLogs(workspaceId, taskId));
    }

    @GetMapping("/{taskId}/reports")
    public ResponseEntity<List<AgentReportResponse>> getTaskReports(
            @PathVariable Long workspaceId, @PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getTaskReports(workspaceId, taskId));
    }
}
