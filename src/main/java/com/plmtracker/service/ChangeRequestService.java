package com.plmtracker.service;

import com.plmtracker.dto.CreateChangeRequestRequest;
import com.plmtracker.dto.UpdateChangeRequestRequest;
import com.plmtracker.exception.BusinessRuleException;
import com.plmtracker.exception.ResourceNotFoundException;
import com.plmtracker.exception.UnknownPartException;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.ChangeRequestStatus;
import com.plmtracker.model.Priority;
import com.plmtracker.plm.PartCatalog;
import com.plmtracker.repository.ChangeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ChangeRequestService {

    private final ChangeRequestRepository repository;
    private final JiraSyncService jiraSyncService;
    private final PartCatalog partCatalog;

    public ChangeRequestService(ChangeRequestRepository repository,
                                JiraSyncService jiraSyncService,
                                PartCatalog partCatalog) {
        this.repository = repository;
        this.jiraSyncService = jiraSyncService;
        this.partCatalog = partCatalog;
    }

    /**
     * Saves the request first (own transaction), then tries to create the Jira issue.
     * Not @Transactional on purpose: the request must stay saved even if Jira is down.
     */
    public ChangeRequest create(CreateChangeRequestRequest req) {
        String partNumber = normalizePartNumber(req.partNumber());
        verifyPartExists(partNumber);

        ChangeRequest cr = new ChangeRequest();
        cr.setPartNumber(partNumber);
        cr.setTitle(req.title().trim());
        cr.setReason(req.reason().trim());
        cr.setPriority(req.priority() != null ? req.priority() : Priority.MEDIUM);
        cr.setCreatedBy(req.createdBy().trim());
        // status = OPEN and jiraSyncState = PENDING come from the entity defaults
        ChangeRequest saved = repository.save(cr);
        jiraSyncService.sync(saved); // marks SYNCED (key + url) or FAILED, never throws
        return saved;
    }

    @Transactional(readOnly = true)
    public ChangeRequest get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ChangeRequest> list(ChangeRequestStatus status, String partNumber) {
        boolean hasPart = partNumber != null && !partNumber.isBlank();
        String part = hasPart ? normalizePartNumber(partNumber) : null;

        if (status != null && hasPart) {
            return repository.findByStatusAndPartNumberOrderByCreatedAtDesc(status, part);
        }
        if (status != null) {
            return repository.findByStatusOrderByCreatedAtDesc(status);
        }
        if (hasPart) {
            return repository.findByPartNumberOrderByCreatedAtDesc(part);
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public ChangeRequest update(Long id, UpdateChangeRequestRequest req) {
        ChangeRequest cr = get(id);
        if (cr.getStatus() == ChangeRequestStatus.CLOSED) {
            throw new BusinessRuleException("Closed change requests cannot be edited");
        }
        cr.setTitle(req.title().trim());
        cr.setReason(req.reason().trim());
        cr.setPriority(req.priority());
        return repository.save(cr);
    }

    /**
     * A change request must point at a real part. Skipped when Mini PLM is not configured.
     * If Mini PLM is down, PartCatalogException propagates (503) and nothing is saved.
     */
    private void verifyPartExists(String partNumber) {
        if (!partCatalog.isEnabled()) {
            return;
        }
        if (partCatalog.findByPartNumber(partNumber).isEmpty()) {
            throw new UnknownPartException(partNumber);
        }
    }

    private String normalizePartNumber(String partNumber) {
        return partNumber.trim().toUpperCase(Locale.ROOT);
    }
}
