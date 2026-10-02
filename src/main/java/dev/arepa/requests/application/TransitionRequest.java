package dev.arepa.requests.application;

import dev.arepa.requests.domain.Status;
import java.util.UUID;

public record TransitionRequest(UUID id, Status nextStatus, long expectedVersion, String actor) {}
