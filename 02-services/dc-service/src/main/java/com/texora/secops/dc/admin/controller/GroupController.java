package com.texora.secops.dc.admin.controller;

import com.texora.secops.dc.admin.dto.AddMemberRequest;
import com.texora.secops.dc.admin.dto.CreateGroupRequest;
import com.texora.secops.dc.admin.dto.GroupMembershipResponse;
import com.texora.secops.dc.admin.dto.GroupResponse;
import com.texora.secops.dc.admin.service.GroupLifecycleService;
import com.texora.secops.dc.dto.DtoMapper;
import com.texora.secops.dc.dto.PageResponse;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.RbacInterceptor;
import com.texora.secops.dc.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupLifecycleService service;

    public GroupController(GroupLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("dc.group.read")
    public PageResponse<GroupResponse> list(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return DtoMapper.page(service.list(caller.getTenantId(), DtoMapper.pageable(page, size, Sort.by("name"))),
                DtoMapper::group);
    }

    /** NOT in the LLD endpoint table, but required: without it no group can ever exist. Flagged in README. */
    @PostMapping
    @RequiresRole("dc.group.write")
    public ResponseEntity<GroupResponse> create(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                                @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse body = DtoMapper.group(service.create(caller, request.getName(), request.getDescription(), request.getOrgUnitId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/members")
    @RequiresRole("dc.group.write")
    public ResponseEntity<GroupMembershipResponse> addMember(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                                             @PathVariable("id") UUID groupId,
                                                             @Valid @RequestBody AddMemberRequest request) {
        GroupMembershipResponse body = DtoMapper.membership(service.addMember(caller, groupId, request.getUserId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
