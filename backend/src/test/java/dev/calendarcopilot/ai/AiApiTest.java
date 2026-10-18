package dev.calendarcopilot.ai;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.calendarcopilot.ApiTestSupport;

@TestPropertySource(properties = "app.ai.api-key=test-key")
class AiApiTest extends ApiTestSupport {

    @MockitoBean
    private GeminiClient gemini;

    private static final String RUBRIC = """
            Philosophy 2450 - Paper 4 rubric
            Your paper must be 1,500-2,000 words and defend a clear thesis.
            Cite at least two course readings.
            """;

    private static final String GOOD_PLAN = """
            {"summary":"Argumentative paper on personal identity.",
             "requirements":[
               {"id":"R1","text":"1,500-2,000 words","documentId":"D1","quote":"must be 1,500-2,000 words"},
               {"id":"R2","text":"Cite two readings","documentId":"D1","quote":"Cite at least two course readings"},
               {"id":"R3","text":"Include a bibliography of 10 books","documentId":"D1",
                "quote":"include a bibliography of ten books"}],
             "tasks":[
               {"title":"Draft a thesis statement","description":"","basis":"SUGGESTED","requirementIds":[],
                "suggestedDueDate":"","subtasks":[{"title":"Reread the readings","basis":"SOURCE","requirementIds":["R2"]}]},
               {"title":"Write the draft","description":"Aim for 1,750 words.","basis":"SOURCE",
                "requirementIds":["R1"],"suggestedDueDate":"","subtasks":[]}],
             "uncertainties":[]}
            """;

    private MockMultipartFile rubric() {
        return new MockMultipartFile("rubric", "rubric.txt", "text/plain", RUBRIC.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void generatesDraftWithVerifiedCitationsWithoutSavingAnything() throws Exception {
        String workspace = createWorkspace();
        when(gemini.generateJson(anyString(), anyList(), any())).thenReturn(GOOD_PLAN);

        mvc.perform(multipart("/api/ai/generate-plan").file(rubric())
                        .param("title", "Complete Philosophy Paper 4")
                        .param("dueAt", "2026-11-02T23:59:00-05:00")
                        .param("timeZone", "America/New_York")
                        .header(WORKSPACE, workspace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(2)))
                .andExpect(jsonPath("$.tasks[0].subtasks[0].basis").value("SOURCE"))
                .andExpect(jsonPath("$.tasks[1].basis").value("SOURCE"))
                .andExpect(jsonPath("$.requirements[0].verification").value("VERIFIED"))
                .andExpect(jsonPath("$.requirements[1].verification").value("VERIFIED"))
                .andExpect(jsonPath("$.requirements[2].verification").value("NOT_FOUND"))
                .andExpect(jsonPath("$.documents[0].name").value("rubric.txt"))
                .andExpect(jsonPath("$.documents[0].category").value("RUBRIC"));

        mvc.perform(get("/api/tasks").header(WORKSPACE, workspace))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.not(hasItem("Draft a thesis statement"))));
    }

    @Test
    void retriesOnceWhenTheModelReturnsMalformedJson() throws Exception {
        String workspace = createWorkspace();
        when(gemini.generateJson(anyString(), anyList(), any())).thenReturn("{oops", GOOD_PLAN);

        mvc.perform(multipart("/api/ai/generate-plan").param("title", "Paper").header(WORKSPACE, workspace))
                .andExpect(status().isOk());
        verify(gemini, times(2)).generateJson(anyString(), anyList(), any());
    }

    @Test
    void failsCleanlyWhenOutputStaysMalformed() throws Exception {
        String workspace = createWorkspace();
        when(gemini.generateJson(anyString(), anyList(), any())).thenReturn("{oops");

        mvc.perform(multipart("/api/ai/generate-plan").param("title", "Paper").header(WORKSPACE, workspace))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The AI returned a plan we couldn't use. Please try again."));
    }

    @Test
    void rejectsUnsupportedFilesBeforeCallingTheModel() throws Exception {
        String workspace = createWorkspace();
        MockMultipartFile docx = new MockMultipartFile("instructions", "essay.docx", null,
                new byte[] {'P', 'K', 3, 4, 0, 0});

        mvc.perform(multipart("/api/ai/generate-plan").file(docx).param("title", "Paper").header(WORKSPACE, workspace))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("essay.docx is not supported. Upload PDF, PNG, JPEG or TXT files."));
        verify(gemini, never()).generateJson(anyString(), anyList(), any());
    }

    @Test
    void requiresATitle() throws Exception {
        mvc.perform(multipart("/api/ai/generate-plan").param("title", " ").header(WORKSPACE, createWorkspace()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmSavesTheReviewedPlanAsATree() throws Exception {
        String workspace = createWorkspace();

        mvc.perform(post("/api/ai/confirm-plan").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Complete Philosophy Paper 4","dueAt":"2026-11-02T23:59:00-05:00",
                                 "tasks":[
                                   {"title":"Choose a thesis","subtasks":[{"title":"Reread readings"}]},
                                   {"title":"Write the draft","dueAt":"2026-10-30T23:59:00-04:00",
                                    "description":"Requirement (rubric.txt): 1,500-2,000 words"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].title").value("Complete Philosophy Paper 4"))
                .andExpect(jsonPath("$[0].origin").value("MANUAL"))
                .andExpect(jsonPath("$[1].origin").value("AI"))
                .andExpect(jsonPath("$[1].parentId").value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$[2].title").value("Reread readings"))
                .andExpect(jsonPath("$[3].dueAt").value("2026-10-31T03:59:00Z"));

        mvc.perform(get("/api/tasks").header(WORKSPACE, workspace))
                .andExpect(jsonPath("$[?(@.origin == 'AI')].title")
                        .value(org.hamcrest.Matchers.hasItems("Choose a thesis", "Reread readings", "Write the draft")));
    }

    @Test
    void confirmCanAttachToAnExistingTaskButNotAnotherWorkspaces() throws Exception {
        String workspace = createWorkspace();
        long parent = createTask(workspace, "{\"title\":\"Existing task\"}");
        String plan = "{\"parentTaskId\":" + parent + ",\"tasks\":[{\"title\":\"Step one\"}]}";

        mvc.perform(post("/api/ai/confirm-plan").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(plan))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].parentId").value((int) parent));

        mvc.perform(post("/api/ai/confirm-plan").header(WORKSPACE, createWorkspace())
                        .contentType(MediaType.APPLICATION_JSON).content(plan))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmValidatesThePlan() throws Exception {
        String workspace = createWorkspace();
        mvc.perform(post("/api/ai/confirm-plan").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"tasks\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/confirm-plan").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tasks\":[{\"title\":\"No root\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/confirm-plan").header(WORKSPACE, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"tasks\":[{\"title\":\" \"}]}"))
                .andExpect(status().isBadRequest());
    }
}
