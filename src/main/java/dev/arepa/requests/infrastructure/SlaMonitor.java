package dev.arepa.requests.infrastructure;

import dev.arepa.requests.application.SlaAlertStore;
import java.time.Clock;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class SlaMonitor {
    private final SlaAlertStore alerts;
    private final Clock clock;
    public SlaMonitor(SlaAlertStore alerts, Clock clock) { this.alerts = alerts; this.clock = clock; }
    @Scheduled(fixedDelayString="${platform.sla-scan-interval-ms:15000}")
    public void scan() {
        try { alerts.detect(clock.instant()); }
        catch (RuntimeException failure) { LoggerFactory.getLogger(SlaMonitor.class).warn("SLA scan failed: {}", failure.getClass().getSimpleName()); }
    }
}
