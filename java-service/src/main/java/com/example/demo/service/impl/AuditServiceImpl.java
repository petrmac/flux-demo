package com.example.demo.service.impl;

import com.example.demo.dto.AuditStatsDto;
import com.example.demo.entity.GreetingAudit;
import com.example.demo.repository.GreetingAuditRepository;
import com.example.demo.service.AuditService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final GreetingAuditRepository greetingAuditRepository;

    @Override
    @Transactional
    public GreetingAudit recordGreeting(String name, String message, String requestSource) {
        long startTime = System.currentTimeMillis();

        // Get current trace and span IDs if available
        String traceId = null;
        String spanId = null;
        try {
            Span currentSpan = Span.current();
            if (currentSpan != null) {
                SpanContext spanContext = currentSpan.getSpanContext();
                if (spanContext.isValid()) {
                    traceId = spanContext.getTraceId();
                    spanId = spanContext.getSpanId();
                }
            }
        } catch (Exception e) {
            log.debug("Could not get trace context: {}", e.getMessage());
        }

        GreetingAudit audit = GreetingAudit.builder()
                .name(name)
                .message(message)
                .requestSource(requestSource)
                .traceId(traceId)
                .spanId(spanId)
                .responseTimeMs(System.currentTimeMillis() - startTime)
                .build();

        GreetingAudit saved = greetingAuditRepository.save(audit);
        log.info("Recorded greeting audit for name: {} with trace: {}", name, traceId);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GreetingAudit> getRecentGreetings(int hours) {
        LocalDateTime startDate = LocalDateTime.now().minusHours(hours);
        return greetingAuditRepository.findRecentGreetings(startDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GreetingAudit> getGreetingsByName(String name) {
        return greetingAuditRepository.findByNameOrderByCreatedAtDesc(name);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditStatsDto getStatistics(int hours) {
        LocalDateTime startDate = LocalDateTime.now().minusHours(hours);
        LocalDateTime endDate = LocalDateTime.now();

        List<GreetingAudit> recentGreetings = greetingAuditRepository.findRecentGreetings(startDate);

        // Calculate statistics
        Map<String, Long> requestsByName = recentGreetings.stream()
                .collect(Collectors.groupingBy(GreetingAudit::getName, Collectors.counting()));

        String mostFrequentName = requestsByName.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        Long uniqueTraces = recentGreetings.stream()
                .map(GreetingAudit::getTraceId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Double averageResponseTime = greetingAuditRepository.getAverageResponseTimeSince(startDate);

        return AuditStatsDto.builder()
                .totalRequests((long) recentGreetings.size())
                .averageResponseTimeMs(averageResponseTime != null ? averageResponseTime : 0.0)
                .requestsByName(requestsByName)
                .periodStart(startDate)
                .periodEnd(endDate)
                .mostFrequentName(mostFrequentName)
                .uniqueTraces(uniqueTraces)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GreetingAudit> getLatestGreeting() {
        return greetingAuditRepository.findTopByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GreetingAudit> getAllAudits() {
        return greetingAuditRepository.findAll();
    }
}