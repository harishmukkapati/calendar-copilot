package dev.calendarcopilot.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.util.unit.DataSize;

/** Shared fixtures for AI tests. */
final class TestAi {

    private TestAi() {
    }

    static AiProperties props(String baseUrl, Duration timeout) {
        return new AiProperties("test-key", "gemini-test", "low", baseUrl, timeout, 8192, 6, DataSize.ofMegabytes(5),
                30, 60_000, 4000, 4, new AiProperties.Limits(5, 20, 200));
    }

    static AiProperties props() {
        return props("http://localhost:1", Duration.ofSeconds(5));
    }

    /** A real PDF with one line of text per page. */
    static byte[] pdf(String... pageLines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : pageLines) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(line);
                    content.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
}
