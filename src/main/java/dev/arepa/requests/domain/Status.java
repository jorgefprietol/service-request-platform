package dev.arepa.requests.domain;

import java.util.Set;

public enum Status {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED;

    public boolean permits(Status next) {
        return switch (this) {
            case OPEN -> Set.of(IN_PROGRESS, CANCELLED).contains(next);
            case IN_PROGRESS -> Set.of(RESOLVED, CANCELLED).contains(next);
            case RESOLVED -> Set.of(IN_PROGRESS, CLOSED).contains(next);
            case CLOSED, CANCELLED -> false;
        };
    }
}
