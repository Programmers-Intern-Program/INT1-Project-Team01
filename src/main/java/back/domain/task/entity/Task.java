package back.domain.task.entity;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    private Long assignedAgentId;

    private Long repositoryId;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String sourceId;

    @Column(columnDefinition = "TEXT")
    private String originalRequest;

    private Task(
            Long workspaceId,
            String title,
            String description,
            TaskType taskType,
            TaskPriority priority,
            Long assignedAgentId,
            Long repositoryId,
            SourceType sourceType,
            String sourceId,
            String originalRequest
    ) {
        this.workspaceId = workspaceId;
        this.title = title;
        this.description = description;
        this.taskType = taskType;
        this.status = TaskStatus.REQUESTED;
        this.priority = priority;
        this.assignedAgentId = assignedAgentId;
        this.repositoryId = repositoryId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.originalRequest = originalRequest;
    }

    public static Task create(
            Long workspaceId,
            String title,
            String description,
            TaskType taskType,
            TaskPriority priority,
            Long assignedAgentId,
            Long repositoryId,
            SourceType sourceType,
            String sourceId,
            String originalRequest
    ) {
        return new Task(
                workspaceId,
                title,
                description,
                taskType,
                priority,
                assignedAgentId,
                repositoryId,
                sourceType,
                sourceId,
                originalRequest
        );
    }

    public void updateStatus(TaskStatus status) {
        this.status = status;
    }
}
