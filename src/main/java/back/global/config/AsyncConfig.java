package back.global.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class AsyncConfig {
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

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
}
