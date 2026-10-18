package dev.calendarcopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.calendarcopilot.ai.PlanDraft.Verification;

class CitationVerifierTest {

    private final CitationVerifier verifier = new CitationVerifier();

    private final SourceDocument rubric = new SourceDocument("D1", "rubric.pdf", DocumentCategory.RUBRIC,
            SourceDocument.Kind.PDF_TEXT,
            "Grading Criteria\nThe paper must include at least three\n  peer-reviewed sources. Use MLA “format” "
                    + "for all citations. Late submis-\nsions lose 10% per day.",
            null, "application/pdf", false);

    @Test
    void verifiesQuotesDespiteWhitespaceCaseAndTypography() {
        assertThat(verifier.verify("must include at least three peer-reviewed sources", rubric))
                .isEqualTo(Verification.VERIFIED);
        assertThat(verifier.verify("Use MLA \"format\" for all citations", rubric))
                .isEqualTo(Verification.VERIFIED);
        assertThat(verifier.verify("Late submissions lose 10% per day", rubric))
                .isEqualTo(Verification.VERIFIED);
    }

    @Test
    void verifiesElidedQuotesInOrder() {
        assertThat(verifier.verify("The paper must include ... peer-reviewed sources", rubric))
                .isEqualTo(Verification.VERIFIED);
        assertThat(verifier.verify("peer-reviewed sources ... The paper must include", rubric))
                .isEqualTo(Verification.NOT_FOUND);
    }

    @Test
    void flagsFabricatedOrTooShortQuotes() {
        assertThat(verifier.verify("must include at least five sources", rubric)).isEqualTo(Verification.NOT_FOUND);
        assertThat(verifier.verify("MLA", rubric)).isEqualTo(Verification.NOT_FOUND);
        assertThat(verifier.verify(null, rubric)).isEqualTo(Verification.NOT_FOUND);
        assertThat(verifier.verify("must include at least three", null)).isEqualTo(Verification.NOT_FOUND);
    }

    @Test
    void cannotVerifyImages() {
        SourceDocument image = new SourceDocument("D2", "shot.png", DocumentCategory.INSTRUCTIONS,
                SourceDocument.Kind.IMAGE, null, TestAi.PNG, "image/png", false);

        assertThat(verifier.verify("anything at all from the image", image)).isEqualTo(Verification.UNVERIFIABLE);
    }
}
