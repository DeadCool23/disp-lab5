package com.nisuev.dips.gatewayservice.resilience;

import com.nisuev.dips.gatewayservice.exception.ServiceUnavailableException;
import com.nisuev.dips.gatewayservice.repository.BonusRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class BonusRollbackQueue {

    public record Task(String username, UUID ticketUid) {}

    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
    private final BonusRepository bonusRepository;
    private final long retryDelayMs;
    private Thread worker;

    public BonusRollbackQueue(BonusRepository bonusRepository,
                              @Value("${resilience.rollback-retry-delay-ms:500}") long retryDelayMs) {
        this.bonusRepository = bonusRepository;
        this.retryDelayMs = retryDelayMs;
    }

    public void enqueue(String username, UUID ticketUid) {
        queue.offer(new Task(username, ticketUid));
    }

    public int size() {
        return queue.size();
    }

    @PostConstruct
    void start() {
        worker = new Thread(this::run, "bonus-rollback-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    void stop() {
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Task task = queue.take();
                while (!tryRollback(task)) {
                    Thread.sleep(retryDelayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean tryRollback(Task task) {
        try {
            bonusRepository.rollback(task.username(), task.ticketUid());
            log.info("Deferred bonus rollback done for ticket {}", task.ticketUid());
            return true;
        } catch (ServiceUnavailableException e) {
            return false;
        } catch (RuntimeException e) {
            log.warn("Deferred bonus rollback failed for ticket {}: {}", task.ticketUid(), e.getMessage());
            return false;
        }
    }
}
