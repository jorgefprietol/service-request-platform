package dev.arepa.requests.api;

import dev.arepa.requests.application.*;
import dev.arepa.requests.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RequestController {
    public record CreateInput(@NotBlank @Size(min=3,max=120) String title,
            @NotBlank @Size(max=2000) String description, @NotNull Priority priority) {}
    public record TemplateInput(@NotBlank @Size(min=3,max=120) String title, @Size(max=2000) String description) {}
    public record TransitionInput(@NotNull Status status) {}
    public record RequestView(ServiceRequest request, boolean overdue) {}
    private final RequestService service;
    private final List<RequestTemplate> templates;
    private final Clock clock;

    public RequestController(RequestService service, List<RequestTemplate> templates, Clock clock) {
        this.service = service; this.templates = templates; this.clock = clock;
    }

    @PostMapping("/requests")
    ResponseEntity<RequestView> create(@Valid @RequestBody CreateInput input,
            @RequestHeader("Idempotency-Key") String key, Authentication auth) {
        return creation(service.create(new RequestDraft(input.title(), input.description(), input.priority()), key, auth.getName()));
    }

    @GetMapping("/templates")
    List<RequestTemplate> templates() { return templates; }

    @PostMapping("/templates/{templateId}/requests")
    ResponseEntity<RequestView> fromTemplate(@PathVariable String templateId, @Valid @RequestBody TemplateInput input,
            @RequestHeader("Idempotency-Key") String key, Authentication auth) {
        var template = templates.stream().filter(item -> item.id().equals(templateId)).findFirst().orElseThrow(NotFoundException::new);
        return creation(service.create(template.instantiate(input.title(), input.description()), key, auth.getName()));
    }

    @GetMapping("/requests")
    List<RequestView> list(@RequestParam(defaultValue="50") int limit, @RequestParam(defaultValue="0") int offset, Authentication auth) {
        return service.list(owner(auth), limit, offset).stream().map(this::view).toList();
    }

    @GetMapping("/requests/{id}")
    ResponseEntity<RequestView> get(@PathVariable UUID id, Authentication auth) {
        var request = service.get(id, owner(auth));
        return ResponseEntity.ok().eTag(Long.toString(request.version())).body(view(request));
    }

    @GetMapping("/requests/{id}/history")
    List<RequestStore.AuditEntry> history(@PathVariable UUID id, Authentication auth) { return service.history(id, owner(auth)); }

    @PostMapping("/requests/{id}/transitions")
    ResponseEntity<RequestView> transition(@PathVariable UUID id, @Valid @RequestBody TransitionInput input,
            @RequestHeader("If-Match") String version, Authentication auth) {
        if (!version.matches("\"[0-9]{1,18}\"")) throw new DomainException("If-Match must contain a quoted numeric version, for example \"0\"");
        var request = service.execute(new TransitionRequest(id, input.status(), Long.parseLong(version.substring(1, version.length()-1)), auth.getName()));
        return ResponseEntity.ok().eTag(Long.toString(request.version())).body(view(request));
    }

    private String owner(Authentication auth) { return auth.getName().equals("operator") ? null : auth.getName(); }
    private RequestView view(ServiceRequest request) { return new RequestView(request, request.overdueAt(clock.instant())); }
    private ResponseEntity<RequestView> creation(RequestService.Creation result) {
        return ResponseEntity.status(result.created() ? 201 : 200)
                .location(URI.create("/api/requests/" + result.request().id()))
                .eTag(Long.toString(result.request().version())).body(view(result.request()));
    }
}
