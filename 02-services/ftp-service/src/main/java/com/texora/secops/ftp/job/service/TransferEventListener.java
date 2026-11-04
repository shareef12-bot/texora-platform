package com.texora.secops.ftp.job.service;

import com.texora.secops.ftp.job.event.QuarantineDestroyedEvent;
import com.texora.secops.ftp.job.event.QuarantineReleasedEvent;
import com.texora.secops.ftp.job.event.TransferJobSubmittedEvent;
import com.texora.secops.ftp.transfer.storage.StagingStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts background work only AFTER the originating transaction has committed, so the worker can always see the
 * rows it was told about. Everything here is event-driven; there are no timers or polling loops.
 */
@Component
public class TransferEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferEventListener.class);

    private final TransferOrchestrationService orchestration;
    private final StagingStorage staging;

    public TransferEventListener(TransferOrchestrationService orchestration, StagingStorage staging) {
        this.orchestration = orchestration;
        this.staging = staging;
    }

    @Async("ftpTransferExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSubmitted(TransferJobSubmittedEvent event) {
        orchestration.run(event.getJobId(), event.getTenantId());
    }

    @Async("ftpTransferExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReleased(QuarantineReleasedEvent event) {
        orchestration.deliverReleased(event.getJobId(), event.getTenantId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDestroyed(QuarantineDestroyedEvent event) {
        LOGGER.info("Destroying quarantined payload for job {}", event.getJobId());
        staging.destroy(event.getTenantId(), event.getJobId());
    }
}
