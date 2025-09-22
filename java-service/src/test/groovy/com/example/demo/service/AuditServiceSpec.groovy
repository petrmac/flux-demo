package com.example.demo.service

import com.example.demo.dto.AuditStatsDto
import com.example.demo.entity.GreetingAudit
import com.example.demo.repository.GreetingAuditRepository
import com.example.demo.service.impl.AuditServiceImpl
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

class AuditServiceSpec extends Specification {

    def greetingAuditRepository = Mock(GreetingAuditRepository)

    @Subject
    def auditService = new AuditServiceImpl(greetingAuditRepository)

    def "should record greeting with trace context"() {
        given: "a greeting request"
        def name = "TestUser"
        def message = "Hello, TestUser!"
        def requestSource = "test"

        when: "recording the greeting"
        def result = auditService.recordGreeting(name, message, requestSource)

        then: "the audit is saved to repository"
        1 * greetingAuditRepository.save(_ as GreetingAudit) >> { GreetingAudit audit ->
            audit.id = 1L
            audit.createdAt = LocalDateTime.now()
            return audit
        }

        and: "the result contains correct data"
        result.id == 1L
        result.name == name
        result.message == message
        result.requestSource == requestSource
    }

    def "should get recent greetings from last N hours"() {
        given: "a time period"
        def hours = 24
        def expectedGreetings = [
            createAudit(1L, "User1", "Hello User1"),
            createAudit(2L, "User2", "Hello User2")
        ]

        when: "fetching recent greetings"
        def result = auditService.getRecentGreetings(hours)

        then: "repository is called with correct time range"
        1 * greetingAuditRepository.findRecentGreetings(_ as LocalDateTime) >> expectedGreetings

        and: "the correct greetings are returned"
        result.size() == 2
        result[0].name == "User1"
        result[1].name == "User2"
    }

    def "should get greetings by name ordered by date"() {
        given: "a name to search for"
        def name = "TestUser"
        def expectedGreetings = [
            createAudit(3L, name, "Latest greeting"),
            createAudit(2L, name, "Middle greeting"),
            createAudit(1L, name, "Oldest greeting")
        ]

        when: "fetching greetings by name"
        def result = auditService.getGreetingsByName(name)

        then: "repository returns greetings ordered by date desc"
        1 * greetingAuditRepository.findByNameOrderByCreatedAtDesc(name) >> expectedGreetings

        and: "results are in correct order"
        result.size() == 3
        result[0].message == "Latest greeting"
        result[2].message == "Oldest greeting"
    }

    def "should calculate statistics for given period"() {
        given: "a time period and audit data"
        def hours = 24
        def startDate = LocalDateTime.now().minusHours(hours)
        def greetings = [
            createAudit(1L, "User1", "Hello", "trace1"),
            createAudit(2L, "User1", "Hi", "trace2"),
            createAudit(3L, "User2", "Hey", "trace3"),
            createAudit(4L, "User3", "Howdy", "trace3") // Same trace as previous
        ]

        when: "calculating statistics"
        def result = auditService.getStatistics(hours)

        then: "repository methods are called correctly"
        1 * greetingAuditRepository.findRecentGreetings(_ as LocalDateTime) >> greetings
        1 * greetingAuditRepository.getAverageResponseTimeSince(_ as LocalDateTime) >> 150.5

        and: "statistics are calculated correctly"
        result.totalRequests == 4
        result.averageResponseTimeMs == 150.5
        result.requestsByName.size() == 3
        result.requestsByName["User1"] == 2L
        result.requestsByName["User2"] == 1L
        result.requestsByName["User3"] == 1L
        result.mostFrequentName == "User1"
        result.uniqueTraces == 3 // trace3 appears twice
    }

    def "should handle empty results when calculating statistics"() {
        given: "no greetings in the period"
        def hours = 1

        when: "calculating statistics"
        def result = auditService.getStatistics(hours)

        then: "repository returns empty list"
        1 * greetingAuditRepository.findRecentGreetings(_ as LocalDateTime) >> []
        1 * greetingAuditRepository.getAverageResponseTimeSince(_ as LocalDateTime) >> null

        and: "statistics handle empty data gracefully"
        result.totalRequests == 0
        result.averageResponseTimeMs == 0.0
        result.requestsByName.isEmpty()
        result.mostFrequentName == "N/A"
        result.uniqueTraces == 0
    }

    def "should get latest greeting"() {
        given: "a latest greeting exists"
        def latestGreeting = createAudit(99L, "LatestUser", "Latest message")

        when: "fetching latest greeting"
        def result = auditService.getLatestGreeting()

        then: "repository returns the latest greeting"
        1 * greetingAuditRepository.findTopByOrderByCreatedAtDesc() >> Optional.of(latestGreeting)

        and: "the correct greeting is returned"
        result.isPresent()
        result.get().id == 99L
        result.get().name == "LatestUser"
    }

    def "should handle no latest greeting"() {
        when: "fetching latest greeting when none exists"
        def result = auditService.getLatestGreeting()

        then: "repository returns empty optional"
        1 * greetingAuditRepository.findTopByOrderByCreatedAtDesc() >> Optional.empty()

        and: "empty optional is returned"
        !result.isPresent()
    }

    def "should get all audits"() {
        given: "multiple audits exist"
        def allAudits = [
            createAudit(1L, "User1", "Message1"),
            createAudit(2L, "User2", "Message2"),
            createAudit(3L, "User3", "Message3")
        ]

        when: "fetching all audits"
        def result = auditService.getAllAudits()

        then: "repository returns all audits"
        1 * greetingAuditRepository.findAll() >> allAudits

        and: "all audits are returned"
        result.size() == 3
        result[0].name == "User1"
        result[2].name == "User3"
    }

    // Helper method to create test audit objects
    private GreetingAudit createAudit(Long id, String name, String message, String traceId = null) {
        return GreetingAudit.builder()
            .id(id)
            .name(name)
            .message(message)
            .requestSource("test")
            .traceId(traceId ?: "trace-${id}")
            .spanId("span-${id}")
            .createdAt(LocalDateTime.now().minusMinutes(id))
            .responseTimeMs(100L + id)
            .build()
    }
}