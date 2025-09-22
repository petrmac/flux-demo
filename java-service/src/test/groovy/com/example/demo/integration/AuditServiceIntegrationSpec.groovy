package com.example.demo.integration

import com.example.demo.entity.GreetingAudit
import com.example.demo.repository.GreetingAuditRepository
import com.example.demo.service.AuditService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification
import spock.lang.Stepwise

import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Stepwise
class AuditServiceIntegrationSpec extends Specification {

    @Autowired
    AuditService auditService

    @Autowired
    GreetingAuditRepository greetingAuditRepository

    def cleanup() {
        greetingAuditRepository.deleteAll()
    }

    def "should persist greeting audit to database"() {
        given: "greeting details"
        def name = "IntegrationUser"
        def message = "Hello from integration test"
        def source = "integration-test"

        when: "recording a greeting"
        def result = auditService.recordGreeting(name, message, source)

        then: "audit is persisted with generated ID"
        result.id != null
        result.name == name
        result.message == message
        result.requestSource == source
        result.createdAt != null

        and: "audit can be retrieved from database"
        def persisted = greetingAuditRepository.findById(result.id)
        persisted.isPresent()
        persisted.get().name == name
    }

    def "should retrieve recent greetings within time window"() {
        given: "multiple greetings at different times"
        def now = LocalDateTime.now()
        saveAudit("Recent1", "Message1", now.minusHours(1))
        saveAudit("Recent2", "Message2", now.minusHours(2))
        saveAudit("Old", "Old Message", now.minusHours(25))

        when: "fetching greetings from last 24 hours"
        def recent = auditService.getRecentGreetings(24)

        then: "only recent greetings are returned"
        recent.size() == 2
        recent.any { it.name == "Recent1" }
        recent.any { it.name == "Recent2" }
        !recent.any { it.name == "Old" }
    }

    def "should calculate accurate statistics from database"() {
        given: "multiple greetings with different response times"
        def now = LocalDateTime.now()
        saveAuditWithResponseTime("User1", "Msg1", now.minusHours(1), 100L, "trace1")
        saveAuditWithResponseTime("User1", "Msg2", now.minusHours(2), 200L, "trace2")
        saveAuditWithResponseTime("User2", "Msg3", now.minusHours(3), 300L, "trace3")
        saveAuditWithResponseTime("User2", "Msg4", now.minusHours(4), 400L, "trace3") // Same trace
        saveAuditWithResponseTime("User3", "Msg5", now.minusHours(30), 500L, "trace4") // Outside window

        when: "calculating statistics for last 24 hours"
        def stats = auditService.getStatistics(24)

        then: "statistics are correctly calculated"
        stats.totalRequests == 4
        stats.averageResponseTimeMs == 250.0 // (100+200+300+400)/4
        stats.requestsByName["User1"] == 2L
        stats.requestsByName["User2"] == 2L
        !stats.requestsByName.containsKey("User3")
        stats.mostFrequentName in ["User1", "User2"] // Both have 2 requests
        stats.uniqueTraces == 3 // trace1, trace2, trace3 (trace3 appears twice)
    }

    def "should find greetings by name ordered by date"() {
        given: "multiple greetings for same user"
        def name = "TestUser"
        def now = LocalDateTime.now()
        saveAudit(name, "Latest", now)
        saveAudit(name, "Middle", now.minusHours(1))
        saveAudit(name, "Oldest", now.minusHours(2))
        saveAudit("OtherUser", "Other", now)

        when: "fetching greetings by name"
        def results = auditService.getGreetingsByName(name)

        then: "only that user's greetings are returned in desc order"
        results.size() == 3
        results[0].message == "Latest"
        results[1].message == "Middle"
        results[2].message == "Oldest"
    }

    def "should retrieve latest greeting from database"() {
        given: "multiple greetings at different times"
        def now = LocalDateTime.now()
        saveAudit("User1", "Older", now.minusMinutes(10))
        def latest = saveAudit("User2", "Latest", now)
        saveAudit("User3", "Middle", now.minusMinutes(5))

        when: "fetching the latest greeting"
        def result = auditService.getLatestGreeting()

        then: "the most recent greeting is returned"
        result.isPresent()
        result.get().message == "Latest"
        result.get().name == "User2"
    }

    def "should handle empty database gracefully"() {
        when: "fetching data from empty database"
        def recent = auditService.getRecentGreetings(24)
        def byName = auditService.getGreetingsByName("NonExistent")
        def latest = auditService.getLatestGreeting()
        def stats = auditService.getStatistics(24)

        then: "empty results are handled properly"
        recent.isEmpty()
        byName.isEmpty()
        !latest.isPresent()
        stats.totalRequests == 0
        stats.averageResponseTimeMs == 0.0
        stats.requestsByName.isEmpty()
    }

    // Helper methods
    private GreetingAudit saveAudit(String name, String message, LocalDateTime createdAt) {
        def audit = GreetingAudit.builder()
            .name(name)
            .message(message)
            .requestSource("test")
            .createdAt(createdAt)
            .responseTimeMs(100L)
            .build()
        return greetingAuditRepository.save(audit)
    }

    private GreetingAudit saveAuditWithResponseTime(String name, String message, LocalDateTime createdAt, Long responseTime, String traceId) {
        def audit = GreetingAudit.builder()
            .name(name)
            .message(message)
            .requestSource("test")
            .createdAt(createdAt)
            .responseTimeMs(responseTime)
            .traceId(traceId)
            .spanId("span-" + System.nanoTime())
            .build()
        return greetingAuditRepository.save(audit)
    }
}