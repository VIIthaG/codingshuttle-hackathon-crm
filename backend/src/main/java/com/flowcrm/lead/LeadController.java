package com.flowcrm.lead;

import com.flowcrm.enums.LeadStatus;
import com.flowcrm.lead.dto.LeadCreateRequest;
import com.flowcrm.lead.dto.LeadResponse;
import com.flowcrm.lead.dto.LeadUpdateRequest;
import com.flowcrm.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse create(
            @Valid @RequestBody LeadCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.create(request, principal);
    }

    @GetMapping
    public Page<LeadResponse> list(
            @RequestParam(required = false) LeadStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.list(status, principal, pageable);
    }

    @GetMapping("/{id}")
    public LeadResponse getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.getById(id, principal);
    }

    @PutMapping("/{id}")
    public LeadResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody LeadUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        leadService.delete(id, principal);
    }
}
