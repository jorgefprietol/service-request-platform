package dev.arepa.requests;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arepa.requests.application.*;
import dev.arepa.requests.domain.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest(properties={
        "platform.auth-mode=development-tokens",
        "spring.datasource.url=jdbc:h2:mem:requests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "platform.requester-token=rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr",
        "platform.operator-token=oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    static final String REQUESTER = "Bearer " + "r".repeat(64);
    static final String OPERATOR = "Bearer " + "o".repeat(64);
    static final String BODY = "{\"title\":\"Restore checkout\",\"description\":\"Investigate elevated error rate\",\"priority\":\"HIGH\"}";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RequestService service;
    @Autowired RequestStore store;
    @Autowired UnitOfWork transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired SlaAlertStore alerts;
    @Autowired AssignmentService assignments;

    JsonNode create(String key, String token) throws Exception {
        return json.readTree(mvc.perform(post("/api/requests").header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("request");
    }

    @Test void requiresAuthenticationAndOperatorRole() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"));
        mvc.perform(get("/api/requests").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/requests/" + UUID.randomUUID() + "/transitions").header("Authorization",REQUESTER).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}").header("If-Match","\"0\"")).andExpect(status().isForbidden());
    }

    @Test void idempotencyReplaysAndRejectsChangedContent() throws Exception {
        var key = UUID.randomUUID().toString();
        var first = create(key, REQUESTER);
        mvc.perform(post("/api/requests").header("Authorization",REQUESTER).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.request.id").value(first.get("id").asText()));
        mvc.perform(post("/api/requests").header("Authorization",REQUESTER).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("HIGH","LOW"))).andExpect(status().isConflict());
        assertThat(store.history(UUID.fromString(first.get("id").asText()))).hasSize(1);
    }

    @Test void workflowUsesVersionAndAudit() throws Exception {
        var id = create(UUID.randomUUID().toString(), REQUESTER).get("id").asText();
        mvc.perform(get("/api/requests/" + id).header("Authorization",REQUESTER)).andExpect(status().isOk()).andExpect(header().string("ETag","\"0\""));
        mvc.perform(post("/api/requests/" + id + "/transitions").header("Authorization",OPERATOR).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag","\"1\""));
        mvc.perform(post("/api/requests/" + id + "/transitions").header("Authorization",OPERATOR).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/requests/" + id + "/transitions").header("Authorization",OPERATOR).header("If-Match","\"1\"").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests/" + id + "/history").header("Authorization",REQUESTER)).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].actor").value("operator"));
    }

    @Test void visibilityIsScopedToPrincipal() throws Exception {
        var key = UUID.randomUUID().toString();
        var operatorRequest = create(key, OPERATOR).get("id").asText();
        mvc.perform(get("/api/requests/" + operatorRequest).header("Authorization",REQUESTER)).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/" + operatorRequest + "/history").header("Authorization",REQUESTER)).andExpect(status().isNotFound());
        var requesterRequest = create(key, REQUESTER).get("id").asText();
        assertThat(requesterRequest).isNotEqualTo(operatorRequest);
        mvc.perform(get("/api/requests").header("Authorization",REQUESTER)).andExpect(status().isOk()).andExpect(jsonPath("$[*].request.owner").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("requester"))));
    }

    @Test void templatesAreIndependentAndUnknownTemplatesReturn404() throws Exception {
        mvc.perform(get("/api/templates").header("Authorization",REQUESTER)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/templates/incident/requests").header("Authorization",REQUESTER).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Investigate checkout\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.request.priority").value("HIGH"));
        mvc.perform(post("/api/templates/missing/requests").header("Authorization",REQUESTER).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Investigate checkout\"}")).andExpect(status().isNotFound());
    }

    @Test void validatesInputHeadersAndPagination() throws Exception {
        mvc.perform(post("/api/requests").header("Authorization",REQUESTER).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadRequest());
        for (var body : new String[]{BODY.replace("Restore checkout"," "), BODY.replace("HIGH","INVALID"), BODY.replace("\"priority\"","\"unknown\"")}) {
            mvc.perform(post("/api/requests").header("Authorization",REQUESTER).header("Idempotency-Key","valid-key").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/requests").header("Authorization",REQUESTER).header("Idempotency-Key","short").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests?limit=101").header("Authorization",REQUESTER)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests?offset=-1").header("Authorization",REQUESTER)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests/not-a-uuid").header("Authorization",REQUESTER)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests?limit=1&offset=0").header("Authorization",OPERATOR)).andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + UUID.randomUUID() + "/transitions").header("Authorization",OPERATOR).header("If-Match","0").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isBadRequest());
    }

    @Test void exposesHealthAndProtectsOperationalMetrics() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/metrics").header("Authorization",REQUESTER)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").header("Authorization",OPERATOR)).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(header().exists("Content-Security-Policy"));
    }

    @Test void transactionRollsBackRequestWhenAuditFails() {
        var draft = new RequestDraft("Transactional request", "Test rollback", Priority.NORMAL);
        var key = UUID.randomUUID().toString();
        var decorated = new RequestStore() {
            public java.util.Optional<ServiceRequest> find(UUID id) { return store.find(id); }
            public java.util.Optional<StoredRequest> findByKey(String k) { return store.findByKey(k); }
            public java.util.List<ServiceRequest> list(String owner,int limit,int offset) { return store.list(owner,limit,offset); }
            public void insert(ServiceRequest r,String k,String f) { store.insert(r,k,f); }
            public boolean update(ServiceRequest r,long v) { return store.update(r,v); }
            public void appendAudit(UUID id,AuditEntry entry) { throw new IllegalStateException("Simulated audit failure"); }
            public java.util.List<AuditEntry> history(UUID id) { return store.history(id); }
        };
        var clock = java.time.Clock.systemUTC();
        var failing = new RequestService(decorated, transactions, new RequestFactory(new PrioritySlaPolicy(),clock),clock);
        assertThatThrownBy(() -> failing.create(draft,key,"requester")).isInstanceOf(IllegalStateException.class);
        assertThat(store.findByKey("requester:" + key)).isEmpty();
    }

    @Test void concurrentCreationHasOneRequestAndOneAudit() throws Exception {
        var draft = new RequestDraft("Concurrent create", "Only one request must persist", Priority.NORMAL);
        var key = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = java.util.stream.IntStream.range(0,8).mapToObj(index -> executor.submit(() -> { start.await(); return service.create(draft,key,"requester"); })).toList();
            start.countDown();
            var results = new java.util.ArrayList<RequestService.Creation>();
            for (var future : futures) results.add(future.get(20,TimeUnit.SECONDS));
            assertThat(results.stream().filter(RequestService.Creation::created).count()).isEqualTo(1);
            assertThat(results.stream().map(result -> result.request().id()).distinct().count()).isEqualTo(1);
            assertThat(store.history(results.getFirst().request().id())).hasSize(1);
        }
    }

    @Test void concurrentTransitionsHaveExactlyOneWinner() throws Exception {
        var request = service.create(new RequestDraft("Concurrent edit","Only one transition may commit",Priority.NORMAL),UUID.randomUUID().toString(),"requester").request();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> {
                start.await();
                try { service.execute(new TransitionRequest(request.id(),Status.IN_PROGRESS,0,"operator")); return true; }
                catch (ConflictException conflict) { return false; }
            };
            var first = executor.submit(action); var second = executor.submit(action);
            start.countDown();
            assertThat(java.util.List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(store.history(request.id())).hasSize(2);
            assertThat(store.find(request.id()).orElseThrow().version()).isEqualTo(1);
        }
    }

    @Test void oidcSubjectsAndRolesDriveVisibilityAndAudit() throws Exception {
        var key = UUID.randomUUID().toString();
        var response = mvc.perform(post("/api/requests").with(jwt().jwt(claims -> claims.subject("alice-subject")).authorities(new SimpleGrantedAuthority("ROLE_REQUESTER")))
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated()).andReturn();
        var id = json.readTree(response.getResponse().getContentAsString()).get("request").get("id").asText();
        mvc.perform(get("/api/requests/" + id).with(jwt().jwt(claims -> claims.subject("bob-subject")).authorities(new SimpleGrantedAuthority("ROLE_REQUESTER")))).andExpect(status().isNotFound());
        // A username/subject literally called operator does NOT grant operator visibility.
        mvc.perform(get("/api/requests/" + id).with(jwt().jwt(claims -> claims.subject("operator")).authorities(new SimpleGrantedAuthority("ROLE_REQUESTER")))).andExpect(status().isNotFound());
        mvc.perform(post("/api/requests/" + id + "/transitions").with(jwt().jwt(claims -> claims.subject("operations-user-uuid")).authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                .header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/requests/" + id + "/history").with(jwt().jwt(claims -> claims.subject("alice-subject")).authorities(new SimpleGrantedAuthority("ROLE_REQUESTER"))))
                .andExpect(jsonPath("$[0].actor").value("alice-subject")).andExpect(jsonPath("$[1].actor").value("operations-user-uuid"));
    }

    @Test void assignmentsRequireRegisteredOperatorsAndCurrentRevision() throws Exception {
        var operatorSubject = UUID.randomUUID().toString();
        mvc.perform(get("/api/me").with(jwt().jwt(claims -> claims.subject(operatorSubject).claim("preferred_username","on-call.engineer")).authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value(operatorSubject));
        var id = create(UUID.randomUUID().toString(),REQUESTER).get("id").asText();
        var body = "{\"operatorSubject\":\"" + operatorSubject + "\"}";
        mvc.perform(post("/api/requests/" + id + "/assignment").header("Authorization",REQUESTER).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + id + "/assignment").header("Authorization",OPERATOR).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content("{\"operatorSubject\":\"unknown\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/requests/" + id + "/assignment").header("Authorization",OPERATOR).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.request.assignedTo").value(operatorSubject)).andExpect(header().string("ETag","\"1\""));
        mvc.perform(post("/api/requests/" + id + "/assignment").header("Authorization",OPERATOR).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/requests/" + id + "/assignment").header("Authorization",OPERATOR).header("If-Match","\"1\"").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(header().string("ETag","\"1\""));
        assertThat(store.history(UUID.fromString(id))).hasSize(2);
        assertThat(store.history(UUID.fromString(id)).getLast().detail()).isEqualTo(operatorSubject);
    }

    @Test void slaAlertsAreDurableDeduplicatedAndSuppressTerminalRequests() {
        var id = UUID.randomUUID();
        var now = java.time.Instant.now();
        var expired = new ServiceRequest(id,"Expired incident","Test alert",Priority.CRITICAL,Status.OPEN,"requester",now.minusSeconds(7201),now.minusSeconds(1),0);
        transactions.execute(() -> { store.insert(expired,"expired:" + id,"f".repeat(64)); store.appendAudit(id,new RequestStore.AuditEntry(0,"CREATED","requester",now)); return null; });
        alerts.detect(now); alerts.detect(now);
        assertThat(alerts.active(100)).extracting(SlaAlertStore.Alert::requestId).contains(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sla_alert WHERE request_id = ?",Long.class,id)).isEqualTo(1);
        service.execute(new TransitionRequest(id,Status.CANCELLED,0,"operator"));
        alerts.detect(now);
        assertThat(alerts.active(100)).extracting(SlaAlertStore.Alert::requestId).doesNotContain(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sla_alert WHERE request_id = ?",Long.class,id)).isEqualTo(1);
    }
}
