package dev.calendarcopilot.event;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import dev.calendarcopilot.ApiTestSupport;

class EventApiTest extends ApiTestSupport {

    @Test
    void createsUpdatesAndDeletesEvent() throws Exception {
        String workspace = createWorkspace();
        String body = mvc.perform(post("/api/events").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Study group","startAt":"2026-11-04T18:00:00-05:00","endAt":"2026-11-04T19:00:00-05:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("blue"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");

        mvc.perform(patch("/api/events/" + id).header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"purple\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("purple"))
                .andExpect(jsonPath("$.title").value("Study group"));

        mvc.perform(delete("/api/events/" + id).header(WORKSPACE, workspace))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/events").header(WORKSPACE, workspace))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    }

    @Test
    void rejectsInvalidEvents() throws Exception {
        String workspace = createWorkspace();
        mvc.perform(post("/api/events").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backwards\",\"startAt\":\"2026-11-04T19:00:00Z\",\"endAt\":\"2026-11-04T18:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/events").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Neon\",\"startAt\":\"2026-11-04T18:00:00Z\",\"endAt\":\"2026-11-04T19:00:00Z\",\"color\":\"#ff00ff\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.color").exists());
        mvc.perform(post("/api/events").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No times\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportsMultiDayAndAllDayEventsUpToTheLengthCap() throws Exception {
        String workspace = createWorkspace();
        mvc.perform(post("/api/events").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Conference","startAt":"2026-11-05T09:00:00-05:00","endAt":"2026-11-07T17:00:00-05:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.endAt").value("2026-11-07T22:00:00Z"));

        String body = mvc.perform(post("/api/events").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Thanksgiving break","allDay":true,
                                 "startAt":"2026-11-25T00:00:00-05:00","endAt":"2026-11-29T23:59:00-05:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allDay").value(true))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");

        mvc.perform(patch("/api/events/" + id).header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allDay\":false}"))
                .andExpect(jsonPath("$.allDay").value(false));

        mvc.perform(post("/api/events").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Typo\",\"startAt\":\"2026-01-01T09:00:00Z\",\"endAt\":\"2026-12-01T09:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Events can be at most 62 days long."));
    }
}
