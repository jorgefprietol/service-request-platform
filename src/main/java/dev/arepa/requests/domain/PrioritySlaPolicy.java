package dev.arepa.requests.domain;

import java.time.Duration;

public final class PrioritySlaPolicy implements SlaPolicy {
    public Duration resolutionTime(Priority priority) {
        return Duration.ofHours(switch (priority) {
            case LOW -> 72;
            case NORMAL -> 24;
            case HIGH -> 8;
            case CRITICAL -> 2;
        });
    }
}
