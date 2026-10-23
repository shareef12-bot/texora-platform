package com.texora.secops.siem.correlation.engine;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory sliding-window state for the correlation engine. Deliberately per-instance (not
 * shared across replicas) for this reference implementation - see README for the documented
 * scale-out follow-up (backing this with a shared store, e.g. Redis, once SIEM runs with more
 * than one replica) so correlation windows are consistent across the whole deployment.
 */
@Component
public class CorrelationWindowStore {

    private final Map<String, ConcurrentLinkedDeque<Instant>> windows = new ConcurrentHashMap<>();
    private final Map<String, Instant> stageCompletions = new ConcurrentHashMap<>();

    /** Records one occurrence at {@code now} under {@code key}, evicts anything older than
     *  {@code windowSeconds}, and returns the resulting in-window count. */
    public int recordAndCount(String key, Instant now, int windowSeconds) {
        ConcurrentLinkedDeque<Instant> deque = windows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        deque.addLast(now);
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (true) {
            Instant head = deque.peekFirst();
            if (head == null || head.isAfter(cutoff)) {
                break;
            }
            deque.pollFirst();
        }
        return deque.size();
    }

    public void markStageComplete(String key, Instant completedAt) {
        stageCompletions.put(key, completedAt);
    }

    public Instant getStageCompletion(String key) {
        return stageCompletions.get(key);
    }

    public void clearStageCompletion(String key) {
        stageCompletions.remove(key);
    }
}
