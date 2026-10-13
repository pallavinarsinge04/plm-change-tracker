package com.plmtracker.controller;

import com.plmtracker.dto.ChangeRequestResponse;
import com.plmtracker.dto.CreateChangeRequestRequest;
import com.plmtracker.dto.UpdateChangeRequestRequest;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.ChangeRequestStatus;
import com.plmtracker.service.ChangeRequestService;
import com.plmtracker.service.JiraSyncService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/change-requests")
public class ChangeRequestController {

    private final ChangeRequestService service;
    private final JiraSyncService jiraSyncService;

    public ChangeRequestController(ChangeRequestService service, JiraSyncService jiraSyncService) {
        this.service = service;
        this.jiraSyncService = jiraSyncService;
    }

    @PostMapping
    public ResponseEntity<ChangeRequestResponse> create(@Valid @RequestBody CreateChangeRequestRequest request,
                                                        Authentication authentication) {
        // The author is whoever is logged in. A createdBy sent in the body is ignored,
        // so nobody can raise a change request in someone else's name.
        CreateChangeRequestRequest withAuthor = new CreateChangeRequestRequest(
                request.partNumber(), request.title(), request.reason(),
                request.priority(), authentication.getName());
        ChangeRequest saved = service.create(withAuthor);
        return ResponseEntity
                .created(URI.create("/api/change-requests/" + saved.getId()))
                .body(ChangeRequestResponse.from(saved));
    }

    @GetMapping
    public List<ChangeRequestResponse> list(
            @RequestParam(required = false) ChangeRequestStatus status,
            @RequestParam(required = false) String partNumber) {
        return service.list(status, partNumber).stream()
                .map(ChangeRequestResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ChangeRequestResponse get(@PathVariable Long id) {
        return ChangeRequestResponse.from(service.get(id));
    }

    @PutMapping("/{id}")
    public ChangeRequestResponse update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateChangeRequestRequest request) {
        return ChangeRequestResponse.from(service.update(id, request));
    }

    /** Retries creating the Jira issue for one request (for example after it was FAILED). */
    @PostMapping("/{id}/jira-sync")
    public ChangeRequestResponse retryJiraSync(@PathVariable Long id) {
        return ChangeRequestResponse.from(jiraSyncService.retry(id));
    }
}
