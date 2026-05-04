package back.domain.slack.repository;

import back.domain.slack.entity.SlackIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlackIntegrationRepository extends JpaRepository<SlackIntegration, Long> {

    /**
     * 해당 Slack 팀과 채널이 이미 연동되어 있는지 확인합니다.
     */
    boolean existsBySlackTeamIdAndSlackChannelId(String slackTeamId, String slackChannelId);
}