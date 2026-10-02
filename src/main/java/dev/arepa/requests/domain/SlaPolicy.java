package dev.arepa.requests.domain;

import java.time.Duration;

@FunctionalInterface
public interface SlaPolicy {
    Duration resolutionTime(Priority priority);
}
