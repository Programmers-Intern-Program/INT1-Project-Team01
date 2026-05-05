package back.domain.task.domain;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task_artifacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskArtifact extends BaseEntity {

    @Column(nullable = false)
    private Long taskId;

    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtifactType artifactType;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String url;

    private TaskArtifact(
            Long taskId,
            Long reportId,
            ArtifactType artifactType,
            String name,
            String url
    ) {
        this.taskId = taskId;
        this.reportId = reportId;
        this.artifactType = artifactType;
        this.name = name;
        this.url = url;
    }

    public static TaskArtifact create(
            Long taskId,
            Long reportId,
            ArtifactType artifactType,
            String name,
            String url
    ) {
        return new TaskArtifact(taskId, reportId, artifactType, name, url);
    }
}