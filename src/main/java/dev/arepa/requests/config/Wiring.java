package dev.arepa.requests.config;

import dev.arepa.requests.application.*;
import dev.arepa.requests.domain.*;
import dev.arepa.requests.infrastructure.*;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class Wiring {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean SlaPolicy slaPolicy() { return new PrioritySlaPolicy(); }
    @Bean RequestFactory requestFactory(SlaPolicy sla, Clock clock) { return new RequestFactory(sla, clock); }
    @Bean RequestStore requestStore(JdbcTemplate jdbc) { return new JdbcRequestStore(jdbc); }
    @Bean UnitOfWork unitOfWork(PlatformTransactionManager manager) { return new SpringUnitOfWork(new TransactionTemplate(manager)); }
    @Bean RequestService requestService(RequestStore store, UnitOfWork transactions, RequestFactory factory, Clock clock) { return new RequestService(store, transactions, factory, clock); }
    @Bean List<RequestTemplate> templates() {
        return List.of(
                new RequestTemplate("access", "Access provisioning", new RequestDraft("Access request", "Provision least-privilege access after owner approval.", Priority.NORMAL)),
                new RequestTemplate("incident", "Service interruption", new RequestDraft("Service incident", "Investigate impact, restore service and document the cause.", Priority.HIGH)));
    }
}
