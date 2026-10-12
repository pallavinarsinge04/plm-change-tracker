package com.plmtracker.repository;

import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.ChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {

    List<ChangeRequest> findAllByOrderByCreatedAtDesc();

    List<ChangeRequest> findByStatusOrderByCreatedAtDesc(ChangeRequestStatus status);

    List<ChangeRequest> findByPartNumberOrderByCreatedAtDesc(String partNumber);

    List<ChangeRequest> findByStatusAndPartNumberOrderByCreatedAtDesc(ChangeRequestStatus status, String partNumber);

    /** Used by the Jira webhook in a later ticket. */
    Optional<ChangeRequest> findByJiraKey(String jiraKey);
}
