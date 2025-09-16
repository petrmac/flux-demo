package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GreetingService {

    @Value("${app.greeting.prefix:Hello}")
    private String greetingPrefix;

    @Value("${app.greeting.suffix:Welcome to our service!}")
    private String greetingSuffix;

    public String generateGreeting(String name) {
        log.debug("Generating greeting for: {}", name);

        String greeting = String.format("%s %s! %s", greetingPrefix, name, greetingSuffix);

        log.info("Generated greeting: {}", greeting);

        return greeting;
    }
}