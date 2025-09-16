package com.example.demo.controller;

import com.example.demo.service.GreetingService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DemoController {

    private final GreetingService greetingService;

    @Value("${app.environment:development}")
    private String environment;

    @Value("${app.version:1.0.0}")
    private String version;

    @Timed(histogram = true)
    @GetMapping("/greeting/{name}")
    public ResponseEntity<Map<String, Object>> greeting(@PathVariable String name) {
        log.info("Greeting request received for: {}", name);

        Map<String, Object> response = new HashMap<>();
        response.put("message", greetingService.generateGreeting(name));
        response.put("timestamp", LocalDateTime.now());
        response.put("environment", environment);
        response.put("version", version);

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
            "fluxcd", true
        ));

        return ResponseEntity.ok(response);
    }
}