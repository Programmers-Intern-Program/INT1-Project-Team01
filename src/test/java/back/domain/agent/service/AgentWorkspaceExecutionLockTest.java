package back.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentWorkspaceExecutionLockTest {

    private final AgentWorkspaceExecutionLock executionLock = new AgentWorkspaceExecutionLock();

    @Test
    @DisplayName("같은 workspacePath 작업은 순차 실행한다")
    void execute_sameWorkspacePath_serializes() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        try {
            Future<String> first = executor.submit(() -> executionLock.execute("/tmp/workspace", () -> {
                firstEntered.countDown();
                awaitUnchecked(releaseFirst);
                return "first";
            }));
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

            Future<String> second = executor.submit(() -> executionLock.execute("/tmp/workspace/", () -> {
                secondEntered.countDown();
                return "second";
            }));

            // then
            assertThat(secondEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(secondEntered.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("다른 workspacePath 작업은 병렬 실행을 허용한다")
    void execute_differentWorkspacePath_allowsConcurrentExecution() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        try {
            Future<String> first = executor.submit(() -> executionLock.execute("/tmp/workspace-a", () -> {
                firstEntered.countDown();
                awaitUnchecked(releaseFirst);
                return "first";
            }));
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

            Future<String> second = executor.submit(() -> executionLock.execute("/tmp/workspace-b", () -> {
                secondEntered.countDown();
                return "second";
            }));

            // then
            assertThat(secondEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
            releaseFirst.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
