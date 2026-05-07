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
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.service.TaskRunService;
import back.domain.task.service.TaskService;
import jakarta.validation.Valid;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.AuthenticatedMember;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskRunService taskRunService;

    @PostMapping
    public ResponseEntity<TaskCreateResponse> createTask(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long workspaceId,
            @Valid @RequestBody TaskCreateRequest request
    ) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);

        return ResponseEntity.ok(taskService.createTask(workspaceId, memberId, request));
    }

    @PostMapping("/run")
    public ResponseEntity<TaskRunResponse> createAndRunTask(
            @PathVariable Long workspaceId, @Valid @RequestBody TaskRunRequest request) {
        return ResponseEntity.ok(taskRunService.createAndRunTask(workspaceId, request));
    }

    @GetMapping
    public ResponseEntity<Page<TaskListResponse>> getTasks(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long workspaceId,
            Pageable pageable
    ) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);

        return ResponseEntity.ok(taskService.getTasks(workspaceId, memberId, pageable));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskDetailResponse> getTask(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId
    ) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);

        return ResponseEntity.ok(taskService.getTask(workspaceId, memberId, taskId));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskStatusUpdateResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @RequestBody TaskStatusUpdateRequest request
    ) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);

        return ResponseEntity.ok(taskService.updateStatus(workspaceId, memberId, taskId, request));
    }

    @GetMapping("/{taskId}/logs")
    public ResponseEntity<List<TaskLogResponse>> getTaskLogs(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId
    ) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);

        return ResponseEntity.ok(taskService.getTaskLogs(workspaceId, memberId, taskId));
    }

    @GetMapping("/{taskId}/reports")
    public ResponseEntity<List<AgentReportResponse>> getTaskReports(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId
    ) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);

        return ResponseEntity.ok(taskService.getTaskReports(workspaceId, memberId, taskId));
    }

    private long resolveAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[TaskController#resolveAuthenticatedMemberId] authenticated member is missing",
                    CommonErrorCode.UNAUTHORIZED.defaultMessage()
            );
        }

        return authenticatedMember.memberId();
    }
}