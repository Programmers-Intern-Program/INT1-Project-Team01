package back.domain.slack.repository;

import back.domain.slack.entity.SlackIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlackIntegrationRepository extends JpaRepository<SlackIntegration, Long> {

    boolean existsBySlackTeamIdAndSlackChannelId(String slackTeamId, String slackChannelId);

    Optional<SlackIntegration> findFirstBySlackTeamId(String slackTeamId);

    Optional<SlackIntegration> findFirstBySlackTeamIdAndSlackChannelId(String slackTeamId, String slackChannelId);
}