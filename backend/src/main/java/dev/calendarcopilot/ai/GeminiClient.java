package dev.calendarcopilot.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.calendarcopilot.common.ApiException;

/**
 * Minimal client for Gemini's REST generateContent endpoint with structured JSON output.
 * Plain RestClient instead of an SDK: explicit timeouts, easy to mock, nothing hidden.
 * The API key is sent in a header and never logged.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    /** One part of the user message: text, or inline bytes (image / scanned PDF). */
    public sealed interface Part permits TextPart, InlinePart {
    }

    public record TextPart(String text) implements Part {
    }

    public record InlinePart(String mimeType, byte[] data) implements Part {
    }

    private final AiProperties props;
    private final RestClient http;

    public GeminiClient(AiProperties props) {
        this.props = props;
        HttpClient jdkClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(props.timeout());
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    /** Returns the model's JSON text. Throws {@link ApiException} with a user-safe message on failure. */
    public String generateJson(String systemInstruction, List<Part> parts, Map<String, Object> responseSchema) {
        if (!props.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI planning is not configured on this server.",
                    "not_configured");
        }
        Map<String, Object> body = requestBody(systemInstruction, parts, responseSchema);
        GenerateResponse response;
        try {
            response = http.post()
                    .uri("/models/{model}:generateContent", props.model())
                    .header("x-goog-api-key", props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GenerateResponse.class);
        } catch (RestClientResponseException e) {
            throw providerError(e);
        } catch (ResourceAccessException e) {
            if (hasCause(e, HttpTimeoutException.class) || hasCause(e, java.net.SocketTimeoutException.class)) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,
                        "The AI took too long to respond. Try again, or use fewer or smaller files.", "timeout");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the AI service. Please try again.",
                    "network_error");
        }
        return extractText(response);
    }

    Map<String, Object> requestBody(String systemInstruction, List<Part> parts, Map<String, Object> schema) {
        List<Map<String, Object>> jsonParts = parts.stream().map(GeminiClient::toJson).toList();

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", schema);
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", props.maxOutputTokens());
        if (props.thinkingLevel() != null && !props.thinkingLevel().isBlank()) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", props.thinkingLevel()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        body.put("contents", List.of(Map.of("role", "user", "parts", jsonParts)));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private static Map<String, Object> toJson(Part part) {
        return switch (part) {
            case TextPart t -> Map.of("text", t.text());
            case InlinePart i -> Map.of("inlineData",
                    Map.of("mimeType", i.mimeType(), "data", Base64.getEncoder().encodeToString(i.data())));
        };
    }

    private static String extractText(GenerateResponse response) {
        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service returned an empty response.", "empty_response");
        }
        if (response.promptFeedback() != null && response.promptFeedback().blockReason() != null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The AI declined to process these materials. Try different files or wording.",
                    "blocked_" + response.promptFeedback().blockReason());
        }
        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service returned no plan.", "no_candidates");
        }
        Candidate candidate = response.candidates().getFirst();
        String finishReason = candidate.finishReason();
        if ("SAFETY".equals(finishReason) || "PROHIBITED_CONTENT".equals(finishReason)
                || "BLOCKLIST".equals(finishReason)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The AI declined to process these materials. Try different files or wording.",
                    "blocked_" + finishReason);
        }
        StringBuilder text = new StringBuilder();
        if (candidate.content() != null && candidate.content().parts() != null) {
            for (ResponsePart part : candidate.content().parts()) {
                if (!Boolean.TRUE.equals(part.thought()) && part.text() != null) {
                    text.append(part.text());
                }
            }
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            // The JSON is cut off; the caller treats this like malformed output.
            log.warn("Gemini response truncated at max output tokens");
        }
        return text.toString();
    }

    private static ApiException providerError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        // Gemini error bodies describe the request problem, not the prompt content.
        log.warn("Gemini returned HTTP {}: {}", status, abbreviate(e.getResponseBodyAsString(), 300));
        if (status == 429) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI service is busy right now. Please try again in a minute.", "provider_rate_limited", 60L);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "The AI service had a problem. Please try again.",
                "provider_http_" + status);
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String abbreviate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ---- Response shape (only the fields we read) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerateResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content, String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<ResponsePart> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponsePart(String text, Boolean thought) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptFeedback(String blockReason) {
    }
}
