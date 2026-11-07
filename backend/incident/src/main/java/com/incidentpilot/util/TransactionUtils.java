package com.incidentpilot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects (Kafka publish, WebSocket push) only after the surrounding transaction commits,
 * so a rolled-back change never produces a ghost event. Outside a transaction it runs immediately.
 * Failures are logged and never propagate to the caller: the database change is already committed.
 */
@Slf4j
public final class TransactionUtils {

    private TransactionUtils() {
    }

    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
        } else {
            runSafely(action);
        }
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Post-commit action failed", e);
        }
    }
}
