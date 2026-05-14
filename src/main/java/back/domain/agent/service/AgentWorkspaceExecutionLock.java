package back.domain.agent.service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

@Service
public class AgentWorkspaceExecutionLock {

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T execute(String workspacePath, Supplier<T> operation) {
        Objects.requireNonNull(operation);
        ReentrantLock lock = locks.computeIfAbsent(requireWorkspacePath(workspacePath), key -> new ReentrantLock());
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private String requireWorkspacePath(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalArgumentException("workspacePath must not be blank");
        }
        String normalized = workspacePath.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
