package dev.arepa.requests.application;

public final class NotFoundException extends RuntimeException {
    public NotFoundException() { super("Request or template not found"); }
}
