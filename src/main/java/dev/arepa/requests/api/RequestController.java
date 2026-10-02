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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api")
public class RequestController {
    public record CreateInput(@NotBlank @Size(min=3,max=120) String title,
            @NotBlank @Size(max=2000) String description, @NotNull Priority priority) {}
    public record TemplateInput(@NotBlank @Size(min=3,max=120) String title, @Size(max=2000) String description) {}
    public record TransitionInput(@NotNull Status status) {}
    public record AssignmentInput(@NotBlank @Size(max=200) String operatorSubject) {}
    public record Identity(String subject, String displayName, List<String> roles) {}
    public record AuthConfig(String mode, String issuer, String clientId, String redirectUri) {}
    public record RequestView(ServiceRequest request, boolean overdue) {}
    private final RequestService service;
    private final List<RequestTemplate> templates;
    private final Clock clock;
    private final AssignmentService assignments;
    private final OperatorDirectory operators;
    private final SlaAlertStore alerts;
    @Value("${platform.auth-mode}") private String authMode;
    @Value("${platform.oidc-issuer}") private String issuer;
    @Value("${platform.public-url}") private String publicUrl;

    public RequestController(RequestService service, List<RequestTemplate> templates, Clock clock,
            AssignmentService assignments, OperatorDirectory operators, SlaAlertStore alerts) {
        this.service = service; this.templates = templates; this.clock = clock;
        this.assignments = assignments; this.operators = operators; this.alerts = alerts;
    }

    @GetMapping("/auth/config")
    AuthConfig authConfig() { return new AuthConfig(authMode,issuer,"service-request-console",publicUrl); }

    @GetMapping("/me")
    Identity identity(Authentication auth) {
        var roles = auth.getAuthorities().stream().map(role -> role.getAuthority().replace("ROLE_", "")).toList();
        var name = auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getClaimAsString("preferred_username") : auth.getName();
        if (name == null || name.isBlank()) name = auth.getName();
        if (name.length() > 200) name = name.substring(0,200);
        if (isOperator(auth)) operators.register(auth.getName(), name);
        return new Identity(auth.getName(),name,roles);
    }

    @GetMapping("/operators")
    List<OperatorDirectory.Operator> operators() { return operators.list(); }

    @GetMapping("/alerts")
    List<SlaAlertStore.Alert> alerts() { return alerts.active(100); }

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
        var request = service.execute(new TransitionRequest(id, input.status(), parseVersion(version), auth.getName()));
        return ResponseEntity.ok().eTag(Long.toString(request.version())).body(view(request));
    }

    @PostMapping("/requests/{id}/assignment")
    ResponseEntity<RequestView> assign(@PathVariable UUID id, @Valid @RequestBody AssignmentInput input,
            @RequestHeader("If-Match") String version, Authentication auth) {
        var request = assignments.execute(new AssignRequest(id,input.operatorSubject(),parseVersion(version),auth.getName()));
        return ResponseEntity.ok().eTag(Long.toString(request.version())).body(view(request));
    }

    private long parseVersion(String version) {
        if (!version.matches("\"[0-9]{1,18}\"")) throw new DomainException("If-Match must contain a quoted numeric version, for example \"0\"");
        return Long.parseLong(version.substring(1,version.length()-1));
    }
    private boolean isOperator(Authentication auth) { return auth.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_OPERATOR")); }
    private String owner(Authentication auth) { return isOperator(auth) ? null : auth.getName(); }
    private RequestView view(ServiceRequest request) { return new RequestView(request, request.overdueAt(clock.instant())); }
    private ResponseEntity<RequestView> creation(RequestService.Creation result) {
        return ResponseEntity.status(result.created() ? 201 : 200)
                .location(URI.create("/api/requests/" + result.request().id()))
                .eTag(Long.toString(result.request().version())).body(view(result.request()));
    }
}
