package dev.calendarcopilot.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Injected instead of calling Instant.now() directly so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
