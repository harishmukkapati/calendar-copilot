package dev.calendarcopilot.ai;

/**
 * An uploaded file after validation, held in memory for one request only.
 * Text documents carry extracted text (which citations are checked against);
 * images and scanned PDFs are sent to the model as bytes and cannot be verified.
 */
public record SourceDocument(
        String id,
        String name,
        DocumentCategory category,
        Kind kind,
        String text,
        byte[] inlineBytes,
        String mimeType,
        boolean truncated) {

    public enum Kind {
        TEXT, PDF_TEXT, PDF_SCANNED, IMAGE
    }

    public boolean hasText() {
        return text != null;
    }

    SourceDocument withText(String newText, boolean wasTruncated) {
        return new SourceDocument(id, name, category, kind, newText, inlineBytes, mimeType, wasTruncated);
    }
}
