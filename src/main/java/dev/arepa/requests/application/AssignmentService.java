package dev.arepa.requests.application;

import dev.arepa.requests.domain.DomainException;
import dev.arepa.requests.domain.ServiceRequest;
import java.time.Clock;

public final class AssignmentService {
    private final RequestStore requests;
    private final UnitOfWork transactions;
    private final OperatorDirectory operators;
    private final Clock clock;

    public AssignmentService(RequestStore requests, UnitOfWork transactions, OperatorDirectory operators, Clock clock) {
        this.requests = requests; this.transactions = transactions; this.operators = operators; this.clock = clock;
    }

    public ServiceRequest execute(AssignRequest command) {
        return transactions.execute(() -> {
            if (!operators.exists(command.operatorSubject())) throw new DomainException("Target subject must be a registered authenticated operator");
            var current = requests.find(command.id()).orElseThrow(NotFoundException::new);
            if (current.version() != command.expectedVersion()) throw new ConflictException("Stale request version");
            var next = current.assignTo(command.operatorSubject());
            if (next == current) return current;
            if (!requests.update(next, command.expectedVersion())) throw new ConflictException("Concurrent assignment; reload the request");
            requests.appendAudit(next.id(), new RequestStore.AuditEntry(next.version(), "ASSIGNED", command.actor(), clock.instant(), command.operatorSubject()));
            return next;
        });
    }
}
