package dev.arepa.requests.application;

import dev.arepa.requests.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class RequestService {
    public record Creation(ServiceRequest request, boolean created) {}
    private final RequestStore store;
    private final UnitOfWork transactions;
    private final RequestFactory factory;
    private final Clock clock;

    public RequestService(RequestStore store, UnitOfWork transactions, RequestFactory factory, Clock clock) {
        this.store = store; this.transactions = transactions; this.factory = factory; this.clock = clock;
    }

    public Creation create(RequestDraft draft, String key, String actor) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,128}")) throw new DomainException("Idempotency-Key must contain 8 to 128 letters, digits, hyphens or underscores");
        var fingerprint = fingerprint(draft, actor);
        // Scope keys to the authenticated principal, avoiding cross-client disclosure.
        var scopedKey = actor + ":" + key;
        try {
            return transactions.execute(() -> {
                var existing = store.findByKey(scopedKey);
                if (existing.isPresent()) return replay(existing.get(), fingerprint);
                var request = factory.create(UUID.randomUUID(), draft, actor);
                store.insert(request, scopedKey, fingerprint);
                store.appendAudit(request.id(), new RequestStore.AuditEntry(0, "CREATED", actor, clock.instant()));
                return new Creation(request, true);
            });
        } catch (DuplicateRequestKey race) {
            // Read in a NEW transaction after the failed PostgreSQL transaction is rolled back.
            return transactions.execute(() -> replay(store.findByKey(scopedKey).orElseThrow(() -> new ConflictException("Concurrent creation; retry the same key")), fingerprint));
        }
    }

    private Creation replay(RequestStore.StoredRequest stored, String fingerprint) {
        if (!stored.fingerprint().equals(fingerprint)) throw new ConflictException("Idempotency key was used with different content");
        return new Creation(stored.request(), false);
    }

    public ServiceRequest get(UUID id, String owner) {
        var request = store.find(id).orElseThrow(NotFoundException::new);
        if (owner != null && !request.owner().equals(owner)) throw new NotFoundException();
        return request;
    }

    public List<ServiceRequest> list(String owner, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new DomainException("Use limit 1..100 and offset >= 0");
        return store.list(owner, limit, offset);
    }

    public ServiceRequest execute(TransitionRequest command) {
        return transactions.execute(() -> {
            var current = get(command.id(), null);
            if (current.version() != command.expectedVersion()) throw new ConflictException("Stale request version");
            var next = current.transitionTo(command.nextStatus());
            if (!store.update(next, command.expectedVersion())) throw new ConflictException("Concurrent update; reload the request");
            store.appendAudit(next.id(), new RequestStore.AuditEntry(next.version(), "STATUS_" + next.status(), command.actor(), clock.instant()));
            return next;
        });
    }

    public List<RequestStore.AuditEntry> history(UUID id, String owner) {
        get(id, owner);
        return store.history(id);
    }

    private static String fingerprint(RequestDraft draft, String actor) {
        try {
            // Length-prefix each field: different descriptions cannot collide through delimiters.
            var fields = List.of(actor, draft.title(), draft.description(), draft.priority().name());
            var canonical = new StringBuilder();
            fields.forEach(field -> canonical.append(field.length()).append(':').append(field));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

}
