package dev.calendarcopilot.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Settings under {@code app.ai.*} in application.properties. */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String apiKey,
        String model,
        String thinkingLevel,
        String baseUrl,
        Duration timeout,
        int maxOutputTokens,
        int maxFiles,
        DataSize maxFileSize,
        int maxPdfPages,
        int maxDocumentChars,
        int maxContextChars,
        int maxConcurrent,
        Limits limits) {

    public record Limits(int perWorkspacePerHour, int perIpPerHour, int globalPerDay) {
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
