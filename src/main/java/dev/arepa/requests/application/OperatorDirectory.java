package dev.arepa.requests.application;

import java.util.List;

public interface OperatorDirectory {
    record Operator(String subject, String displayName) {}
    void register(String subject, String displayName);
    boolean exists(String subject);
    List<Operator> list();
}
