package com.texora.secops.dc.sync.controller;

import com.texora.secops.dc.DcConstants;
import com.texora.secops.dc.dto.DtoMapper;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.RbacInterceptor;
import com.texora.secops.dc.security.RequiresRole;
import com.texora.secops.dc.sync.dto.SyncJobResponse;
import com.texora.secops.dc.sync.dto.SyncTriggerRequest;
import com.texora.secops.dc.sync.service.DirectorySyncOrchestrator;
import com.texora.secops.dc.sync.service.HrmsSyncService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlled directory synchronization. source=LDAP_PUSH (default) re-pushes DC -> LDAP; HRMS_PULL pulls HRMS -> DC (ADR D-2). */
@RestController
@RequestMapping("/api/v1/directory-synchronization")
public class DirectorySyncController {

    private final DirectorySyncOrchestrator orchestrator;
    private final HrmsSyncService hrmsSync;

    public DirectorySyncController(DirectorySyncOrchestrator orchestrator, HrmsSyncService hrmsSync) {
        this.orchestrator = orchestrator;
        this.hrmsSync = hrmsSync;
    }

    @PostMapping
    @RequiresRole("dc.sync.trigger")
    public SyncJobResponse trigger(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                   @Valid @RequestBody(required = false) SyncTriggerRequest request) {
        String source = request == null || request.getSource() == null ? DcConstants.SYNC_SOURCE_LDAP_PUSH : request.getSource();
        if (DcConstants.SYNC_SOURCE_HRMS_PULL.equals(source)) {
            return DtoMapper.syncJob(hrmsSync.pull(caller.getTenantId(), caller.getSubjectId()));
        }
        return DtoMapper.syncJob(orchestrator.triggerFullSync(caller));
    }
}
