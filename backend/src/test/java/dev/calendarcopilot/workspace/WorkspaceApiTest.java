package dev.calendarcopilot.workspace;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.calendarcopilot.ApiTestSupport;

class WorkspaceApiTest extends ApiTestSupport {

    @Test
    void newWorkspaceIsSeededWithDemoData() throws Exception {
        String workspace = createWorkspace();

        mvc.perform(get("/api/workspaces/current").header(WORKSPACE, workspace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/New_York"));
        mvc.perform(get("/api/tasks").header(WORKSPACE, workspace))
                .andExpect(jsonPath("$.length()").value(greaterThan(5)))
                .andExpect(jsonPath("$[?(@.parentId != null)]").isNotEmpty());
        mvc.perform(get("/api/events").header(WORKSPACE, workspace))
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void createWithoutBodyUsesDefaultZone() throws Exception {
        mvc.perform(post("/api/workspaces"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone").value("America/New_York"));
    }

    @Test
    void rejectsUnknownTimeZoneAndUnknownWorkspace() throws Exception {
        mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"Mars/Olympus_Mons\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/workspaces/current").header(WORKSPACE, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsUnderApiPrefix() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
