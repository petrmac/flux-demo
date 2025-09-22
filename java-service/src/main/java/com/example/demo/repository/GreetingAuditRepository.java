package com.example.demo.repository;

import com.example.demo.entity.GreetingAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GreetingAuditRepository extends JpaRepository<GreetingAudit, Long> {

    List<GreetingAudit> findByNameOrderByCreatedAtDesc(String name);

    List<GreetingAudit> findByTraceId(String traceId);

    @Query("SELECT g FROM GreetingAudit g WHERE g.createdAt >= :startDate ORDER BY g.createdAt DESC")
    List<GreetingAudit> findRecentGreetings(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(g) FROM GreetingAudit g WHERE g.name = :name AND g.createdAt >= :startDate")
    Long countGreetingsByNameSince(@Param("name") String name, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT AVG(g.responseTimeMs) FROM GreetingAudit g WHERE g.createdAt >= :startDate")
    Double getAverageResponseTimeSince(@Param("startDate") LocalDateTime startDate);

    Optional<GreetingAudit> findTopByOrderByCreatedAtDesc();
}