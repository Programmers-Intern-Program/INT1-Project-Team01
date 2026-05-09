package back.domain.slack.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import back.domain.slack.entity.SlackEventLog;

public interface SlackEventLogRepository extends JpaRepository<SlackEventLog, Long> {

    boolean existsBySlackEventId(String slackEventId);

    @Query("""
            select eventLog
            from SlackEventLog eventLog
            left join fetch eventLog.integration integration
            left join fetch integration.workspace
            where eventLog.id = :id
            """)
    Optional<SlackEventLog> findByIdWithIntegrationAndWorkspace(@Param("id") Long id);
}
