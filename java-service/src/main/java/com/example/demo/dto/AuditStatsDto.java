package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditStatsDto {
    private Long totalRequests;
    private Double averageResponseTimeMs;
    private Map<String, Long> requestsByName;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String mostFrequentName;
    private Long uniqueTraces;
}