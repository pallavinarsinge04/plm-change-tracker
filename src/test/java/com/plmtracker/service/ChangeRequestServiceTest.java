package com.plmtracker.service;

import com.plmtracker.dto.CreateChangeRequestRequest;
import com.plmtracker.dto.UpdateChangeRequestRequest;
import com.plmtracker.exception.BusinessRuleException;
import com.plmtracker.exception.ResourceNotFoundException;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.ChangeRequestStatus;
import com.plmtracker.model.JiraSyncState;
import com.plmtracker.model.Priority;
import com.plmtracker.repository.ChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeRequestServiceTest {

    @Mock
    private ChangeRequestRepository repository;

    private ChangeRequestService service;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestService(repository);
        when(repository.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_setsDefaultsAndNormalizesPartNumber() {
        var req = new CreateChangeRequestRequest(
                "  p-3001 ", " Stronger bolt ", " Failed fatigue test ", null, " Pallavi ");

        ChangeRequest saved = service.create(req);

        assertThat(saved.getPartNumber()).isEqualTo("P-3001");
        assertThat(saved.getTitle()).isEqualTo("Stronger bolt");
        assertThat(saved.getCreatedBy()).isEqualTo("Pallavi");
        assertThat(saved.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(saved.getStatus()).isEqualTo(ChangeRequestStatus.OPEN);
        assertThat(saved.getJiraSyncState()).isEqualTo(JiraSyncState.PENDING);
        assertThat(saved.getJiraKey()).isNull();
    }

    @Test
    void create_keepsGivenPriority() {
        var req = new CreateChangeRequestRequest("P-1", "t", "r", Priority.CRITICAL, "me");

        assertThat(service.create(req).getPriority()).isEqualTo(Priority.CRITICAL);
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void list_withoutFilters_returnsAll() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(new ChangeRequest()));

        assertThat(service.list(null, null)).hasSize(1);
        verify(repository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void list_withStatusAndPart_usesCombinedQueryWithUpperCasePart() {
        when(repository.findByStatusAndPartNumberOrderByCreatedAtDesc(ChangeRequestStatus.OPEN, "P-1"))
                .thenReturn(List.of(new ChangeRequest()));

        assertThat(service.list(ChangeRequestStatus.OPEN, "p-1")).hasSize(1);
    }

    @Test
    void update_openRequest_changesEditableFields() {
        ChangeRequest existing = new ChangeRequest();
        existing.setId(1L);
        existing.setTitle("old");
        existing.setReason("old reason");
        existing.setPriority(Priority.LOW);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        ChangeRequest updated = service.update(1L,
                new UpdateChangeRequestRequest(" new title ", " new reason ", Priority.HIGH));

        assertThat(updated.getTitle()).isEqualTo("new title");
        assertThat(updated.getReason()).isEqualTo("new reason");
        assertThat(updated.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void update_closedRequest_isRejected() {
        ChangeRequest closed = new ChangeRequest();
        closed.setId(2L);
        closed.setStatus(ChangeRequestStatus.CLOSED);
        when(repository.findById(2L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.update(2L,
                new UpdateChangeRequestRequest("t", "r", Priority.LOW)))
                .isInstanceOf(BusinessRuleException.class);

        verify(repository, never()).save(any(ChangeRequest.class));
    }
}
