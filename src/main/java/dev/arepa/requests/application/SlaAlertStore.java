package dev.arepa.requests.application;

import dev.arepa.requests.domain.Priority;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SlaAlertStore {
    record Alert(UUID requestId, String title, Priority priority, Instant dueAt, Instant detectedAt, String assignedTo) {}
    int detect(Instant now);
    List<Alert> active(int limit);
}
