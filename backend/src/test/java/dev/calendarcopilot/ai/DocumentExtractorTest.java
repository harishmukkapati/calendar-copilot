package dev.calendarcopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import dev.calendarcopilot.ai.DocumentExtractor.UploadedFile;
import dev.calendarcopilot.common.ApiException;

class DocumentExtractorTest {

    private final DocumentExtractor extractor = new DocumentExtractor(TestAi.props());

    @Test
    void extractsTextFromPdf() throws Exception {
        byte[] pdf = TestAi.pdf("Essay must be 1500 to 2000 words and cite three peer-reviewed sources.");

        SourceDocument doc = extractOne(new MockMultipartFile("rubric", "rubric.pdf", "application/pdf", pdf));

        assertThat(doc.kind()).isEqualTo(SourceDocument.Kind.PDF_TEXT);
        assertThat(doc.text()).contains("cite three peer-reviewed sources");
        assertThat(doc.inlineBytes()).isNull();
    }

    @Test
    void sendsPdfWithoutTextAsScannedBytes() throws Exception {
        byte[] pdf = TestAi.pdf("Hi");

        SourceDocument doc = extractOne(new MockMultipartFile("f", "scan.pdf", "application/pdf", pdf));

        assertThat(doc.kind()).isEqualTo(SourceDocument.Kind.PDF_SCANNED);
        assertThat(doc.hasText()).isFalse();
        assertThat(doc.inlineBytes()).isEqualTo(pdf);
    }

    @Test
    void detectsTypeFromContentNotFileName() {
        SourceDocument png = extractOne(new MockMultipartFile("f", "notes.txt", "text/plain", TestAi.PNG));
        assertThat(png.kind()).isEqualTo(SourceDocument.Kind.IMAGE);
        assertThat(png.mimeType()).isEqualTo("image/png");

        SourceDocument text = extractOne(new MockMultipartFile("f", "rubric.pdf", "application/pdf",
                "Plain text pretending to be a PDF".getBytes(StandardCharsets.UTF_8)));
        assertThat(text.kind()).isEqualTo(SourceDocument.Kind.TEXT);
    }

    @Test
    void rejectsUnsupportedBinaryFiles() {
        byte[] docx = {'P', 'K', 3, 4, 20, 0, 6, 0, 0, 0}; // ZIP container (Word, Excel, etc.)

        assertThatThrownBy(() -> extractOne(new MockMultipartFile("f", "essay.docx", null, docx)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsOversizedEmptyAndTooManyFiles() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> extractOne(new MockMultipartFile("f", "big.txt", null, big)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));

        assertThatThrownBy(() -> extractOne(new MockMultipartFile("f", "empty.txt", null, new byte[0])))
                .hasMessageContaining("empty");

        MockMultipartFile file = new MockMultipartFile("f", "a.txt", null, "x".getBytes(StandardCharsets.UTF_8));
        List<UploadedFile> seven = Collections.nCopies(7, new UploadedFile(DocumentCategory.SUPPORTING, file));
        assertThatThrownBy(() -> extractor.extract(seven)).hasMessageContaining("at most 6 files");
    }

    @Test
    void rejectsPdfWithTooManyPages() throws Exception {
        String[] pages = new String[31];
        java.util.Arrays.fill(pages, "Page text that is long enough to count as real text.");
        byte[] pdf = TestAi.pdf(pages);

        assertThatThrownBy(() -> extractOne(new MockMultipartFile("f", "long.pdf", null, pdf)))
                .hasMessageContaining("more than 30 pages");
    }

    @Test
    void sanitizesDisplayNames() {
        assertThat(DocumentExtractor.displayName("C:\\Users\\me\\<rubric>\".pdf", 0)).isEqualTo("rubric.pdf");
        assertThat(DocumentExtractor.displayName("../../etc/passwd", 0)).isEqualTo("passwd");
        assertThat(DocumentExtractor.displayName("", 2)).isEqualTo("document-3");
    }

    private SourceDocument extractOne(MockMultipartFile file) {
        return extractor.extract(List.of(new UploadedFile(DocumentCategory.RUBRIC, file))).getFirst();
    }
}
