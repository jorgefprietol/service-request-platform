package dev.arepa.requests.application;

import java.util.UUID;

public record AssignRequest(UUID id, String operatorSubject, long expectedVersion, String actor) {}
