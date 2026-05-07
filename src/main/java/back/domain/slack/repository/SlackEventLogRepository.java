package back.domain.slack.repository;

import back.domain.slack.entity.SlackEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlackEventLogRepository extends JpaRepository<SlackEventLog, Long> {
    boolean existsBySlackEventId(String slackEventId);
}