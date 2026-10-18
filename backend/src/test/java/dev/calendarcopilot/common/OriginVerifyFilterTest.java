package dev.calendarcopilot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginVerifyFilterTest {

    @Test
    void rejectsApiRequestsWithoutTheSecretHeader() throws Exception {
        MockHttpServletResponse response = run(new OriginVerifyFilter("s3cret"), "/api/tasks", null);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        MockHttpServletResponse response = run(new OriginVerifyFilter("s3cret"), "/api/tasks", "guess");
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsCorrectSecret() throws Exception {
        MockHttpServletResponse response = run(new OriginVerifyFilter("s3cret"), "/api/tasks", "s3cret");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void isDisabledWhenNoSecretIsConfigured() throws Exception {
        MockHttpServletResponse response = run(new OriginVerifyFilter(""), "/api/tasks", null);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse run(OriginVerifyFilter filter, String uri, String header)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (header != null) {
            request.addHeader(OriginVerifyFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
