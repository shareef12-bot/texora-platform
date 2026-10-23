package com.texora.secops.dc.admin.controller;

import com.texora.secops.dc.admin.dto.CreateUserRequest;
import com.texora.secops.dc.admin.dto.UpdateUserRequest;
import com.texora.secops.dc.admin.dto.UserResponse;
import com.texora.secops.dc.admin.service.UserLifecycleService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Users are an HRMS projection (ADR D-2). POST materializes an HRMS employee into DC; PATCH only toggles the
 * DC-owned security lock. HRMS-owned fields in a request body yield 422 HRMS_OWNED_FIELD.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserLifecycleService service;

    public UserController(UserLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("dc.user.read")
    public PageResponse<UserResponse> list(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return DtoMapper.page(service.list(caller.getTenantId(), DtoMapper.pageable(page, size, Sort.by("username"))),
                DtoMapper::user);
    }

    @PostMapping
    @RequiresRole("dc.user.write")
    public ResponseEntity<UserResponse> create(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                                               @Valid @RequestBody CreateUserRequest request) {
        UserResponse body = DtoMapper.user(service.materialize(caller, request.getHrmsEmployeeId(), request.getInitialGroups()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{id}")
    @RequiresRole("dc.user.write")
    public UserResponse update(@RequestAttribute(RbacInterceptor.CALLER_ATTRIBUTE) CallerContext caller,
                               @PathVariable("id") UUID id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return DtoMapper.user(service.setSecurityLock(caller, id, request.getSecurityLocked().booleanValue()));
    }
}
