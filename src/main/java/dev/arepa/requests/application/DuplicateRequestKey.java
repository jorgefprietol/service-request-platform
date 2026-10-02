package dev.arepa.requests.application;

public final class DuplicateRequestKey extends RuntimeException {
    public DuplicateRequestKey(Throwable cause) { super("Request key already exists", cause); }
}
