package dev.arepa.requests.application;

import dev.arepa.requests.domain.ServiceRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestStore {
    record StoredRequest(ServiceRequest request, String fingerprint) {}
    record AuditEntry(long version, String action, String actor, Instant occurredAt, String detail) {
        public AuditEntry(long version, String action, String actor, Instant occurredAt) {
            this(version, action, actor, occurredAt, null);
        }
    }
    Optional<ServiceRequest> find(UUID id);
    Optional<StoredRequest> findByKey(String key);
    List<ServiceRequest> list(String owner, int limit, int offset);
    void insert(ServiceRequest request, String key, String fingerprint);
    boolean update(ServiceRequest request, long expectedVersion);
    void appendAudit(UUID id, AuditEntry entry);
    List<AuditEntry> history(UUID id);
}
