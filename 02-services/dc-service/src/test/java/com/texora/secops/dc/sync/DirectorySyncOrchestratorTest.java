package com.texora.secops.dc.sync;

import com.texora.secops.dc.TestSupport;
import com.texora.secops.dc.audit.service.DcAuditRecorder;
import com.texora.secops.dc.domain.DcGroup;
import com.texora.secops.dc.domain.DcSyncJob;
import com.texora.secops.dc.domain.DcUser;
import com.texora.secops.dc.repository.GroupRepository;
import com.texora.secops.dc.repository.MembershipRepository;
import com.texora.secops.dc.repository.SyncJobRepository;
import com.texora.secops.dc.repository.SyncJobSweepRepository;
import com.texora.secops.dc.repository.UserRepository;
import com.texora.secops.dc.sync.client.LdapControlPlaneClient;
import com.texora.secops.dc.sync.client.SyncEnvelope;
import com.texora.secops.dc.sync.service.DirectorySyncOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectorySyncOrchestratorTest {

    private final UUID tenant = UUID.randomUUID();
    private SyncJobRepository jobs;
    private LdapControlPlaneClient ldap;
    private DcAuditRecorder audit;
    private DirectorySyncOrchestrator orchestrator;
    private UserRepository users;
    private GroupRepository groups;
    private MembershipRepository memberships;
    private final SyncEnvelope envelope = new SyncEnvelope();

    @BeforeEach
    void setUp() throws Exception {
        jobs = mock(SyncJobRepository.class);
        ldap = mock(LdapControlPlaneClient.class);
        audit = mock(DcAuditRecorder.class);
        users = mock(UserRepository.class);
        groups = mock(GroupRepository.class);
        memberships = mock(MembershipRepository.class);
        TransactionTemplate newTx = mock(TransactionTemplate.class);
        when(newTx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        Executor direct = Runnable::run;
        when(ldap.toEnvelope(any())).thenReturn(envelope);
        orchestrator = new DirectorySyncOrchestrator(jobs, mock(SyncJobSweepRepository.class), ldap, users, groups, memberships,
                audit, TestSupport.fixedTime(), newTx, direct, 5, 5L, 600L, 30L, 100);
    }

    private DcSyncJob pendingJob(int attempts) {
        DcSyncJob j = new DcSyncJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(tenant);
        j.setEntityType("USER");
        j.setStatus("PENDING");
        j.setAttempts(attempts);
        when(jobs.findByTenantIdAndId(tenant, j.getId())).thenReturn(Optional.of(j));
        return j;
    }

    @Test
    void sameIdempotencyKeyReturnsExistingJobWithoutNewWork() {
        DcSyncJob existing = new DcSyncJob();
        when(jobs.findByTenantIdAndIdempotencyKey(tenant, "k1")).thenReturn(Optional.of(existing));
        DcSyncJob result = orchestrator.requestSync(tenant, "USER", "u1", "UPSERT", "k1", UUID.randomUUID(), new HashMap<String, Object>());
        assertSame(existing, result);
        verify(jobs, never()).save(any());
    }

    @Test
    void newRequestIsRecordedPendingThenSentOnBrokerAck() throws Exception {
        DcSyncJob[] saved = new DcSyncJob[1];
        when(jobs.save(any(DcSyncJob.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return saved[0];
        });
        when(jobs.findByTenantIdAndId(any(), any())).thenAnswer(inv -> Optional.ofNullable(saved[0]));
        DcSyncJob job = orchestrator.requestSync(tenant, "USER", "u1", "UPSERT", "k2", UUID.randomUUID(), new HashMap<String, Object>());
        assertEquals(tenant, job.getTenantId());
        assertEquals("LDAP_PUSH", job.getSource());
        verify(ldap).publish(envelope);          // executor is synchronous in this test
        assertEquals("SENT", job.getStatus());
        assertEquals(1, job.getAttempts());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void publishFailureKeepsJobPendingWithBackoff() throws Exception {
        DcSyncJob job = pendingJob(0);
        doThrow(new IllegalStateException("kafka down")).when(ldap).publish(any());
        orchestrator.publishJob(tenant, job.getId());
        assertEquals("PENDING", job.getStatus());
        assertEquals(1, job.getAttempts());
        assertNotNull(job.getNextRetryAt());
        assertNotNull(job.getErrorSummary());
        verify(ldap, never()).publishToDeadLetter(any(), anyString());
    }

    @Test
    void exhaustedRetriesGoToDeadLetterQueueAndRaiseAlert() throws Exception {
        DcSyncJob job = pendingJob(4); // 5th failure == max attempts
        doThrow(new IllegalStateException("kafka down")).when(ldap).publish(any());
        orchestrator.publishJob(tenant, job.getId());
        verify(ldap).publishToDeadLetter(eq(envelope), anyString());
        assertEquals("DEAD_LETTERED", job.getStatus());
        assertEquals(5, job.getAttempts());
        assertNull(job.getNextRetryAt());
        verify(audit).recordIndependent(eq(tenant), any(), eq("SYNC_DEAD_LETTERED"), anyString(), anyString(), eq("ALERT"), anyMap());
    }

    @Test
    void unreachableDlqNeverDropsTheMessage() throws Exception {
        DcSyncJob job = pendingJob(4);
        doThrow(new IllegalStateException("kafka down")).when(ldap).publish(any());
        doThrow(new IllegalStateException("dlq down")).when(ldap).publishToDeadLetter(any(), anyString());
        orchestrator.publishJob(tenant, job.getId());
        assertEquals("PENDING", job.getStatus());
        assertNotNull(job.getNextRetryAt());
    }

    @Test
    void alreadySentJobIsNotRepublished() throws Exception {
        DcSyncJob job = pendingJob(1);
        job.setStatus("SENT");
        orchestrator.publishJob(tenant, job.getId());
        verify(ldap, never()).publish(any());
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertEquals(5L, orchestrator.backoffSeconds(1));
        assertEquals(20L, orchestrator.backoffSeconds(3));
        assertEquals(600L, orchestrator.backoffSeconds(30));
    }

    @Test
    void fullSyncEnqueuesEveryEntityAndRecordsCount() {
        DcUser u = new DcUser();
        u.setId(UUID.randomUUID());
        u.setUsername("jsmith");
        u.setStatus("ACTIVE");
        DcGroup g = new DcGroup();
        g.setId(UUID.randomUUID());
        g.setName("vpn-admins");
        when(users.findByTenantId(tenant)).thenReturn(Collections.singletonList(u));
        when(groups.findByTenantId(tenant)).thenReturn(Collections.singletonList(g));
        when(memberships.findAllByTenant(tenant)).thenReturn(Collections.<com.texora.secops.dc.domain.DcGroupMembership>emptyList());
        DcSyncJob parent = orchestrator.triggerFullSync(TestSupport.caller(tenant, UUID.randomUUID(), "dc.sync.trigger"));
        assertEquals(2, parent.getRecordsProcessed());
        assertEquals("COMPLETED", parent.getStatus());
        verify(audit).record(eq(tenant), any(), eq("DIRECTORY_SYNC_TRIGGERED"), anyString(), anyString(), eq("SUCCESS"), anyMap());
    }
}
