package dev.arepa.requests.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DomainTest {
    private final Instant now = Instant.parse("2026-10-02T12:00:00Z");
    private final RequestDraft draft = new RequestDraft("Restore service", "Investigate outage", Priority.HIGH);
    private ServiceRequest request(Status status) {
        return new ServiceRequest(UUID.randomUUID(), draft.title(), draft.description(), draft.priority(), status, "requester", now, now.plusSeconds(60), 0);
    }

    @ParameterizedTest @EnumSource(Priority.class)
    void factoryUsesSlaStrategy(Priority priority) {
        var policy = new PrioritySlaPolicy();
        var factory = new RequestFactory(policy, Clock.fixed(now, ZoneOffset.UTC));
        var result = factory.create(UUID.randomUUID(), new RequestDraft("Title", "Description", priority), "owner");
        assertThat(result.slaDueAt()).isEqualTo(now.plus(policy.resolutionTime(priority)));
        assertThat(result.status()).isEqualTo(Status.OPEN);
        assertThat(result.version()).isZero();
    }

    static Stream<Arguments> transitions() {
        return Stream.of(Status.values()).flatMap(from -> Stream.of(Status.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest @MethodSource("transitions")
    void lifecycleAllowsExactlyTheDeclaredTransitions(Status from, Status to) {
        boolean allowed = switch (from) {
            case OPEN -> to == Status.IN_PROGRESS || to == Status.CANCELLED;
            case IN_PROGRESS -> to == Status.RESOLVED || to == Status.CANCELLED;
            case RESOLVED -> to == Status.CLOSED || to == Status.IN_PROGRESS;
            case CLOSED, CANCELLED -> false;
        };
        var initial = request(from);
        if (allowed) {
            assertThat(initial.transitionTo(to).status()).isEqualTo(to);
            assertThat(initial.transitionTo(to).version()).isEqualTo(1);
            assertThat(initial.status()).isEqualTo(from);
        } else assertThatThrownBy(() -> initial.transitionTo(to)).isInstanceOf(DomainException.class);
    }

    @Test void overdueBoundaryAndTerminalRequests() {
        assertThat(request(Status.OPEN).overdueAt(now.plusSeconds(60))).isFalse();
        assertThat(request(Status.IN_PROGRESS).overdueAt(now.plusSeconds(61))).isTrue();
        for (var status : new Status[]{Status.RESOLVED, Status.CLOSED, Status.CANCELLED}) assertThat(request(status).overdueAt(now.plusSeconds(100))).isFalse();
    }

    @Test void builderDefaultsAndNormalizesValues() {
        assertThat(RequestDraft.builder().title("  New request  ").description(" description ").build())
                .isEqualTo(new RequestDraft("New request", "description", Priority.NORMAL));
    }

    @Test void templateCreatesIndependentImmutableDrafts() {
        var template = new RequestTemplate("incident", "Incident", draft);
        var first = template.instantiate("First incident", null);
        var second = template.instantiate("Second incident", "New description");
        assertThat(first.description()).isEqualTo(draft.description());
        assertThat(second.description()).isEqualTo("New description");
        assertThat(template.prototype()).isEqualTo(draft);
        assertThat(first.priority()).isEqualTo(Priority.HIGH);
    }

    @Test void draftRejectsInvalidInput() {
        assertThatThrownBy(() -> new RequestDraft("ab", "d", Priority.LOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RequestDraft("a".repeat(121), "d", Priority.LOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RequestDraft("Title", " ", Priority.LOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RequestDraft("Title", "a".repeat(2001), Priority.LOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RequestDraft(null, "d", Priority.LOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RequestDraft("Title", null, Priority.LOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RequestDraft("Title", "d", null)).isInstanceOf(NullPointerException.class);
    }

    @Test void aggregateRejectsInvalidState() {
        assertThatThrownBy(() -> new ServiceRequest(UUID.randomUUID(), "Title", "d", Priority.LOW, Status.OPEN, " ", now, now.plusSeconds(1), 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ServiceRequest(UUID.randomUUID(), "Title", "d", Priority.LOW, Status.OPEN, "owner", now, now, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ServiceRequest(UUID.randomUUID(), "Title", "d", Priority.LOW, Status.OPEN, "owner", now, now.plusSeconds(1), -1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> request(Status.OPEN).transitionTo(null)).isInstanceOf(DomainException.class);
    }
}
