package back.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 애플리케이션의 비동기 처리(@Async)를 위한 Thread Pool 전역 설정입니다.
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.initialize();
        return executor;
    }

    /**
     * Slack 이벤트 처리를 전담하는 커스텀 스레드 풀을 생성합니다.
     */
    @Bean(name = "slackEventTaskExecutor")
    public Executor slackEventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);    // 평상시 동시 처리 예상량 기준
        executor.setMaxPoolSize(20);    // 트래픽 급증 시 최대 확장 범위
        executor.setQueueCapacity(50);  // 순간 burst 흡수 버퍼. 초과 시 rejection handler에서 로그 남김
        executor.setThreadNamePrefix("SlackEvent-Async-");

        executor.setRejectedExecutionHandler((Runnable r, ThreadPoolExecutor exec) -> {
            log.error("[Critical] Slack Async Queue가 꽉 찼습니다. 이벤트를 처리할 수 없어 유실됩니다. 모니터링 확인 요망.");
        });

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}