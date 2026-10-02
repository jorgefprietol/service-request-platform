package dev.arepa.requests.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ServiceRequest(UUID id, String title, String description, Priority priority,
        Status status, String owner, Instant createdAt, Instant slaDueAt, long version) {
    public ServiceRequest {
        Objects.requireNonNull(id);
        new RequestDraft(title, description, priority);
        Objects.requireNonNull(status);
        if (owner == null || owner.isBlank()) throw new DomainException("Owner is required");
        Objects.requireNonNull(createdAt);
        if (slaDueAt == null || !slaDueAt.isAfter(createdAt)) throw new DomainException("SLA deadline must follow creation");
        if (version < 0) throw new DomainException("Version must be non-negative");
    }

    public ServiceRequest transitionTo(Status next) {
        if (next == null || !status.permits(next)) throw new DomainException("Transition from " + status + " to " + next + " is not allowed");
        return new ServiceRequest(id, title, description, priority, next, owner, createdAt, slaDueAt, version + 1);
    }

    public boolean overdueAt(Instant now) {
        return (status == Status.OPEN || status == Status.IN_PROGRESS) && now.isAfter(slaDueAt);
    }
}
