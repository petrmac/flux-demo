package com.example.demo.controller;

import com.example.demo.dto.AuditStatsDto;
import com.example.demo.entity.GreetingAudit;
import com.example.demo.service.AuditService;
import com.example.demo.service.GreetingService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DemoController {

    private final GreetingService greetingService;
    private final AuditService auditService;

    @Value("${app.environment:development}")
    private String environment;

    @Value("${app.version:1.0.0}")
    private String version;

    @Timed(histogram = true)
    @GetMapping("/greeting/{name}")
    public ResponseEntity<Map<String, Object>> greeting(@PathVariable String name,
                                                         @RequestParam(defaultValue = "api") String source) {
        log.info("Greeting request received for: {} from source: {}", name, source);

        String message = greetingService.generateGreeting(name);

        // Record in database with trace context
        GreetingAudit audit = auditService.recordGreeting(name, message, source);

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        response.put("environment", environment);
        response.put("version", version);
        response.put("auditId", audit.getId());
        response.put("traceId", audit.getTraceId());

        return ResponseEntity.ok(response);
    }

    @Timed
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "demo-service");
        response.put("version", version);

        return ResponseEntity.ok(response);
    }

    @Timed
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "demo-service");
        response.put("version", version);
        response.put("environment", environment);
        response.put("timestamp", LocalDateTime.now());
        response.put("features", Map.of(
            "opentelemetry", true,
            "prometheus", true,
            "sops", true,
            "fluxcd", true,
            "database", true,
            "liquibase", true
        ));

        return ResponseEntity.ok(response);
    }

    @Timed
    @GetMapping("/audits")
    public ResponseEntity<List<GreetingAudit>> getAudits(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "24") int hours) {

        List<GreetingAudit> audits;
        if (name != null) {
            audits = auditService.getGreetingsByName(name);
            log.info("Retrieved {} audits for name: {}", audits.size(), name);
        } else {
            audits = auditService.getRecentGreetings(hours);
            log.info("Retrieved {} recent audits from last {} hours", audits.size(), hours);
        }

        return ResponseEntity.ok(audits);
    }

    @Timed
    @GetMapping("/audits/stats")
    public ResponseEntity<AuditStatsDto> getAuditStatistics(
            @RequestParam(defaultValue = "24") int hours) {

        AuditStatsDto stats = auditService.getStatistics(hours);
        log.info("Generated statistics for last {} hours: {} total requests",
                hours, stats.getTotalRequests());

        return ResponseEntity.ok(stats);
    }

    @Timed
    @GetMapping("/audits/latest")
    public ResponseEntity<GreetingAudit> getLatestAudit() {
        return auditService.getLatestGreeting()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Timed
    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(@RequestBody Map<String, Object> request) {
        String message = request.getOrDefault("message", "Echo").toString();
        log.info("Echo request received: {}", message);

        // Record echo in database
        GreetingAudit audit = auditService.recordGreeting("echo", message, "echo-endpoint");

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        response.put("echo", true);
        response.put("auditId", audit.getId());

        return ResponseEntity.ok(response);
    }

    @Timed
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody Map<String, Object> request) {
        String scenario = request.getOrDefault("scenario", "normal").toString();
        int delay = Integer.parseInt(request.getOrDefault("delay", "0").toString());

        log.info("Simulate request: scenario={}, delay={}", scenario, delay);

        // Record simulation in database
        String message = String.format("Simulation: %s with delay %dms", scenario, delay);
        GreetingAudit audit = auditService.recordGreeting("simulation", message, "simulate-endpoint");

        // Add artificial delay if requested
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Simulate different scenarios
        switch (scenario) {
            case "error":
                throw new RuntimeException("Simulated error");
            case "slow":
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("scenario", scenario);
        response.put("delay", delay);
        response.put("timestamp", LocalDateTime.now());
        response.put("auditId", audit.getId());

        return ResponseEntity.ok(response);
    }
}