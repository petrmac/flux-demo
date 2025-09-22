package com.example.demo.service;

import com.example.demo.dto.AuditStatsDto;
import com.example.demo.entity.GreetingAudit;

import java.util.List;
import java.util.Optional;

public interface AuditService {
    GreetingAudit recordGreeting(String name, String message, String requestSource);
    List<GreetingAudit> getRecentGreetings(int hours);
    List<GreetingAudit> getGreetingsByName(String name);
    AuditStatsDto getStatistics(int hours);
    Optional<GreetingAudit> getLatestGreeting();
    List<GreetingAudit> getAllAudits();
}