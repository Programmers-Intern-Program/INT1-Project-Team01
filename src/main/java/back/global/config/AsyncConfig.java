package back.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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
        // 큐가 포화되어 작업 등록이 거절되면 예외를 호출자에게 던지지 않고 경고 로그만 남기고 작업을 폐기한다.
        // afterCommit 콜백 안에서 RejectedExecutionException이 터지면 잡을 곳이 없어 메일이 조용히 유실되는 것을 방지하기 위함.
        executor.setRejectedExecutionHandler((runnable, poolExecutor) ->
                log.warn(
                        "[AsyncConfig#emailTaskExecutor] 이메일 작업 실행자가 포화 상태라 작업 등록이 거절되었습니다. activeCount={}, queueSize={}",
                        poolExecutor.getActiveCount(),
                        poolExecutor.getQueue().size()));
        executor.initialize();
        return executor;
    }

    /**
     * Slack 이벤트는 best-effort 모델이다.
     *
     * 큐 포화 시:
     * - 이벤트는 drop될 수 있음
     * - 하지만 예외를 던지지 않아야 트랜잭션 롤백을 방지함
     * - DB에는 RECEIVED 로그가 정상 저장되어야 함
     */
    @Bean(name = "slackEventTaskExecutor")
    public Executor slackEventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);    // 평상시 동시 처리 예상량 기준
        executor.setMaxPoolSize(20);    // 트래픽 급증 시 최대 확장 범위
        executor.setQueueCapacity(50);  // 순간 burst 흡수 버퍼. 초과 시 rejection handler에서 로그 남김
        executor.setThreadNamePrefix("SlackEvent-Async-");
        executor.setRejectedExecutionHandler((r, exec) ->
                log.error(
                        "[AsyncConfig#slackEventTaskExecutor] Slack async queue full. " +
                                "activeCount={}, poolSize={}, queueSize={}",
                        exec.getActiveCount(),
                        exec.getPoolSize(),
                        exec.getQueue().size()
                )
        );
        // 큐 포화로 drop된 이벤트는 DB에 RECEIVED 상태로 잔류함.
        // 트래픽 급증 시 유실 허용 범위를 초과한다면 RECEIVED 상태 기반 재처리 스케줄러 도입을 고려할 것.

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "taskExecutionTaskExecutor")
    public Executor taskExecutionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("task-execution-async-");
        // 기본 AbortPolicy를 사용해 큐 포화 시 @Async 호출자에게 TaskRejectedException을 전파한다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}