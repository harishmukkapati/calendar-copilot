package dev.calendarcopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.calendarcopilot.ai.GeminiClient.InlinePart;
import dev.calendarcopilot.ai.GeminiClient.TextPart;
import dev.calendarcopilot.common.ApiException;

/** Runs the client against a real local HTTP server, including timeouts. */
class GeminiClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private GeminiClient client(int status, String body, long delayMs, Duration timeout) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastApiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
        return new GeminiClient(TestAi.props(baseUrl, timeout));
    }

    private String call(GeminiClient client) {
        return client.generateJson("system", List.of(new TextPart("hello"), new InlinePart("image/png", TestAi.PNG)),
                PlanPrompt.responseSchema());
    }

    @Test
    void sendsStructuredOutputRequestAndReturnsNonThoughtText() throws Exception {
        GeminiClient client = client(200, """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"text":"thinking...","thought":true},{"text":"{\\"tasks\\":"},{"text":"[]}"}]}}]}
                """, 0, Duration.ofSeconds(5));

        assertThat(call(client)).isEqualTo("{\"tasks\":[]}");
        assertThat(lastPath.get()).isEqualTo("/v1beta/models/gemini-test:generateContent");
        assertThat(lastApiKey.get()).isEqualTo("test-key");
        JsonNode body = JsonMapper.builder().build().readTree(lastBody.get());
        JsonNode config = body.path("generationConfig");
        assertThat(config.path("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(config.path("thinkingConfig").path("thinkingLevel").asString()).isEqualTo("low");
        assertThat(config.path("responseSchema").path("propertyOrdering").get(0).asString()).isEqualTo("summary");
        assertThat(body.path("systemInstruction").path("parts").get(0).path("text").asString()).isEqualTo("system");
        JsonNode parts = body.path("contents").get(0).path("parts");
        assertThat(parts.get(0).path("text").asString()).isEqualTo("hello");
        assertThat(parts.get(1).path("inlineData").path("mimeType").asString()).isEqualTo("image/png");
    }

    @Test
    void mapsProviderErrorsToUserSafeStatuses() throws Exception {
        assertThatThrownBy(() -> call(client(500, "{\"error\":{\"message\":\"internal\"}}", 0, Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        stop();
        assertThatThrownBy(() -> call(client(429, "{}", 0, Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void reportsBlockedContent() throws Exception {
        GeminiClient client = client(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", 0,
                Duration.ofSeconds(5));

        assertThatThrownBy(() -> call(client)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(e.getReason()).isEqualTo("blocked_SAFETY");
        });
    }

    @Test
    void timesOutSlowResponses() throws Exception {
        GeminiClient client = client(200, "{}", 1500, Duration.ofMillis(300));

        assertThatThrownBy(() -> call(client)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }
}
