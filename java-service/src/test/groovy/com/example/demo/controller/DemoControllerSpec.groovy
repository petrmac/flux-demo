package com.example.demo.controller

import com.example.demo.dto.AuditStatsDto
import com.example.demo.entity.GreetingAudit
import com.example.demo.service.AuditService
import com.example.demo.service.GreetingService
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.LocalDateTime

class DemoControllerSpec extends Specification {

    def greetingService = Mock(GreetingService)
    def auditService = Mock(AuditService)

    @Subject
    def controller = new DemoController(greetingService, auditService)

    def setup() {
        controller.environment = "test"
        controller.version = "1.0.0-test"
    }

    def "should handle greeting request and record audit"() {
        given: "a name and source"
        def name = "World"
        def source = "api"
        def expectedMessage = "Hello, World!"
        def audit = createAudit(1L, name, expectedMessage, "trace123")

        when: "greeting endpoint is called"
        def response = controller.greeting(name, source)

        then: "greeting service generates message"
        1 * greetingService.generateGreeting(name) >> expectedMessage

        and: "audit service records the greeting"
        1 * auditService.recordGreeting(name, expectedMessage, source) >> audit

        and: "response is successful with audit info"
        response.statusCode == HttpStatus.OK
        response.body.message == expectedMessage
        response.body.auditId == 1L
        response.body.traceId == "trace123"
        response.body.environment == "test"
        response.body.version == "1.0.0-test"
    }

    def "should use default source when not provided"() {
        given: "only a name"
        def name = "DefaultTest"
        def audit = createAudit(2L, name, "Hello, DefaultTest!")

        when: "greeting endpoint is called without source"
        def response = controller.greeting(name, "api")

        then: "audit is recorded with default source"
        1 * greetingService.generateGreeting(name) >> "Hello, DefaultTest!"
        1 * auditService.recordGreeting(name, "Hello, DefaultTest!", "api") >> audit

        and: "response is successful"
        response.statusCode == HttpStatus.OK
    }

    @Unroll
    def "should get audits filtered by #filterType"() {
        given: "filter parameters"
        def expectedAudits = [
            createAudit(1L, filterName ?: "User1", "Message1"),
            createAudit(2L, filterName ?: "User2", "Message2")
        ]

        when: "fetching audits"
        def response = controller.getAudits(filterName, hours)

        then: "correct service method is called"
        if (filterName) {
            1 * auditService.getGreetingsByName(filterName) >> expectedAudits
            0 * auditService.getRecentGreetings(_)
        } else {
            0 * auditService.getGreetingsByName(_)
            1 * auditService.getRecentGreetings(hours) >> expectedAudits
        }

        and: "audits are returned"
        response.statusCode == HttpStatus.OK
        response.body.size() == 2

        where:
        filterType      | filterName | hours
        "name"          | "TestUser" | 24
        "recent hours"  | null       | 48
        "default hours" | null       | 24
    }

    def "should get audit statistics"() {
        given: "a time period"
        def hours = 24
        def stats = AuditStatsDto.builder()
            .totalRequests(100L)
            .averageResponseTimeMs(250.5)
            .requestsByName(["User1": 50L, "User2": 30L, "User3": 20L])
            .periodStart(LocalDateTime.now().minusHours(hours))
            .periodEnd(LocalDateTime.now())
            .mostFrequentName("User1")
            .uniqueTraces(75L)
            .build()

        when: "fetching statistics"
        def response = controller.getAuditStatistics(hours)

        then: "audit service calculates statistics"
        1 * auditService.getStatistics(hours) >> stats

        and: "statistics are returned"
        response.statusCode == HttpStatus.OK
        response.body.totalRequests == 100L
        response.body.averageResponseTimeMs == 250.5
        response.body.mostFrequentName == "User1"
        response.body.uniqueTraces == 75L
    }

    def "should get latest audit when exists"() {
        given: "a latest audit exists"
        def latestAudit = createAudit(99L, "LatestUser", "Latest message")

        when: "fetching latest audit"
        def response = controller.getLatestAudit()

        then: "audit service returns the latest"
        1 * auditService.getLatestGreeting() >> Optional.of(latestAudit)

        and: "audit is returned"
        response.statusCode == HttpStatus.OK
        response.body.id == 99L
        response.body.name == "LatestUser"
    }

    def "should return 404 when no latest audit exists"() {
        when: "fetching latest audit when none exists"
        def response = controller.getLatestAudit()

        then: "audit service returns empty"
        1 * auditService.getLatestGreeting() >> Optional.empty()

        and: "404 is returned"
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "should handle echo request and record audit"() {
        given: "an echo request"
        def request = [message: "Echo this message"]
        def audit = createAudit(3L, "echo", "Echo this message")

        when: "echo endpoint is called"
        def response = controller.echo(request)

        then: "audit service records the echo"
        1 * auditService.recordGreeting("echo", "Echo this message", "echo-endpoint") >> audit

        and: "response contains echo confirmation"
        response.statusCode == HttpStatus.OK
        response.body.message == "Echo this message"
        response.body.echo == true
        response.body.auditId == 3L
    }

    def "should handle echo with default message"() {
        given: "an empty echo request"
        def request = [:]
        def audit = createAudit(4L, "echo", "Echo")

        when: "echo endpoint is called without message"
        def response = controller.echo(request)

        then: "default message is used"
        1 * auditService.recordGreeting("echo", "Echo", "echo-endpoint") >> audit

        and: "response uses default"
        response.statusCode == HttpStatus.OK
        response.body.message == "Echo"
    }

    def "should simulate normal scenario"() {
        given: "a simulation request"
        def request = [scenario: "normal", delay: "100"]
        def audit = createAudit(5L, "simulation", "Simulation: normal with delay 100ms")

        when: "simulate endpoint is called"
        def response = controller.simulate(request)

        then: "audit records the simulation"
        1 * auditService.recordGreeting("simulation", "Simulation: normal with delay 100ms", "simulate-endpoint") >> audit

        and: "response is successful"
        response.statusCode == HttpStatus.OK
        response.body.scenario == "normal"
        response.body.delay == 100
        response.body.auditId == 5L
    }

    def "should simulate error scenario"() {
        given: "an error simulation request"
        def request = [scenario: "error", delay: "0"]
        def audit = createAudit(6L, "simulation", "Simulation: error with delay 0ms")

        when: "simulate endpoint is called with error scenario"
        controller.simulate(request)

        then: "audit records before error"
        1 * auditService.recordGreeting("simulation", "Simulation: error with delay 0ms", "simulate-endpoint") >> audit

        and: "error is thrown"
        thrown(RuntimeException)
    }

    def "should handle health check"() {
        when: "health endpoint is called"
        def response = controller.health()

        then: "health status is returned"
        response.statusCode == HttpStatus.OK
        response.body.status == "UP"
        response.body.service == "demo-service"
        response.body.version == "1.0.0-test"
    }

    def "should provide service info with all features"() {
        when: "info endpoint is called"
        def response = controller.info()

        then: "service information is returned"
        response.statusCode == HttpStatus.OK
        response.body.service == "demo-service"
        response.body.version == "1.0.0-test"
        response.body.environment == "test"

        and: "all features are listed"
        response.body.features.opentelemetry == true
        response.body.features.prometheus == true
        response.body.features.database == true
        response.body.features.liquibase == true
        response.body.features.sops == true
        response.body.features.fluxcd == true
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