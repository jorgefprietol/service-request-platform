package dev.arepa.requests.domain;

import java.util.Objects;

public record RequestDraft(String title, String description, Priority priority) {
    public RequestDraft {
        title = Objects.requireNonNull(title, "Title is required").strip();
        description = Objects.requireNonNull(description, "Description is required").strip();
        Objects.requireNonNull(priority, "Priority is required");
        if (title.length() < 3 || title.length() > 120) throw new DomainException("Title must contain 3 to 120 characters");
        if (description.isEmpty() || description.length() > 2000) throw new DomainException("Description must contain 1 to 2000 characters");
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String title;
        private String description;
        private Priority priority = Priority.NORMAL;
        public Builder title(String value) { title = value; return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder priority(Priority value) { priority = value; return this; }
        public RequestDraft build() { return new RequestDraft(title, description, priority); }
    }
}
