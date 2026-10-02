package dev.arepa.requests.domain;

import java.time.Clock;
import java.util.UUID;

public final class RequestFactory {
    private final SlaPolicy sla;
    private final Clock clock;

    public RequestFactory(SlaPolicy sla, Clock clock) { this.sla = sla; this.clock = clock; }

    public ServiceRequest create(UUID id, RequestDraft draft, String owner) {
        var now = clock.instant();
        return new ServiceRequest(id, draft.title(), draft.description(), draft.priority(), Status.OPEN,
                owner, now, now.plus(sla.resolutionTime(draft.priority())), 0);
    }
}
