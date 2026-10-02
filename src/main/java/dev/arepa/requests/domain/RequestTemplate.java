package dev.arepa.requests.domain;

public record RequestTemplate(String id, String name, RequestDraft prototype) {
    public RequestDraft instantiate(String title, String description) {
        return RequestDraft.builder()
                .title(title)
                .description(description == null ? prototype.description() : description)
                .priority(prototype.priority())
                .build();
    }
}
