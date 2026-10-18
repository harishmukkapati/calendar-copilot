package dev.calendarcopilot.task;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.calendarcopilot.ApiTestSupport;

class TaskApiTest extends ApiTestSupport {

    private String workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = createWorkspace();
    }

    @Test
    void createsTaskWithSubtasksAndReturnsThemInList() throws Exception {
        long parent = createTask(workspace, """
                {"title":"  Complete Philosophy Paper 4  ","description":"Essay","dueAt":"2026-11-02T23:59:00-05:00"}
                """);
        long first = createTask(workspace, "{\"title\":\"Write outline\",\"parentId\":" + parent + "}");
        long second = createTask(workspace, "{\"title\":\"Draft body\",\"parentId\":" + parent + "}");

        mvc.perform(get("/api/tasks").header(WORKSPACE, workspace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + parent + ")].title").value(hasItem("Complete Philosophy Paper 4")))
                .andExpect(jsonPath("$[?(@.id == " + parent + ")].dueAt").value(hasItem("2026-11-03T04:59:00Z")))
                .andExpect(jsonPath("$[?(@.id == " + parent + ")].origin").value(hasItem("MANUAL")))
                .andExpect(jsonPath("$[?(@.parentId == " + parent + ")].id")
                        .value(hasItems((int) first, (int) second)))
                .andExpect(jsonPath("$[?(@.id == " + first + ")].sortOrder").value(hasItem(0)))
                .andExpect(jsonPath("$[?(@.id == " + second + ")].sortOrder").value(hasItem(1)));
    }

    @Test
    void updatesFieldsMarksCompleteAndClearsDueDate() throws Exception {
        long id = createTask(workspace, "{\"title\":\"Lab report\",\"dueAt\":\"2026-11-01T17:00:00Z\"}");

        mvc.perform(patch("/api/tasks/" + id).header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Chemistry lab report\",\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Chemistry lab report"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.dueAt").value("2026-11-01T17:00:00Z"));

        mvc.perform(patch("/api/tasks/" + id).header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearDueAt\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").value(nullValue()))
                .andExpect(jsonPath("$.title").value("Chemistry lab report"));
    }

    @Test
    void deletingParentDeletesSubtasks() throws Exception {
        long parent = createTask(workspace, "{\"title\":\"Parent\"}");
        long child = createTask(workspace, "{\"title\":\"Child\",\"parentId\":" + parent + "}");
        long grandchild = createTask(workspace, "{\"title\":\"Grandchild\",\"parentId\":" + child + "}");

        mvc.perform(delete("/api/tasks/" + parent).header(WORKSPACE, workspace))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tasks").header(WORKSPACE, workspace))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) parent))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) child))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) grandchild))));

        mvc.perform(delete("/api/tasks/" + parent).header(WORKSPACE, workspace))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherWorkspaceCannotReadOrModifyTasks() throws Exception {
        long id = createTask(workspace, "{\"title\":\"Private task\"}");
        String other = createWorkspace();

        mvc.perform(get("/api/tasks").header(WORKSPACE, other))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
        mvc.perform(patch("/api/tasks/" + id).header(WORKSPACE, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/" + id).header(WORKSPACE, other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/tasks").header(WORKSPACE, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky subtask\",\"parentId\":" + id + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsSubtasksBeyondMaxDepth() throws Exception {
        long level1 = createTask(workspace, "{\"title\":\"Level 1\"}");
        long level2 = createTask(workspace, "{\"title\":\"Level 2\",\"parentId\":" + level1 + "}");
        long level3 = createTask(workspace, "{\"title\":\"Level 3\",\"parentId\":" + level2 + "}");

        mvc.perform(post("/api/tasks").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Level 4\",\"parentId\":" + level3 + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Subtasks can be nested at most 3 levels deep."));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/tasks").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());

        mvc.perform(post("/api/tasks").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backwards\",\"startAt\":\"2026-11-02T10:00:00Z\",\"dueAt\":\"2026-11-01T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/tasks").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Orphan\",\"parentId\":999999}"))
                .andExpect(status().isNotFound());

        long id = createTask(workspace, "{\"title\":\"Keep my title\"}");
        mvc.perform(patch("/api/tasks/" + id).header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAValidExistingWorkspace() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks").header(WORKSPACE, "not-a-uuid"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").header(WORKSPACE, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Nowhere\"}"))
                .andExpect(status().isNotFound());
    }
}
