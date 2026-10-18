package dev.calendarcopilot.ai;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import dev.calendarcopilot.common.ApiException;

/**
 * Validates uploads and turns them into {@link SourceDocument}s. File types are
 * detected from content (magic bytes), never trusted from the name or Content-Type.
 * Nothing is executed, written to disk, or stored.
 */
@Component
public class DocumentExtractor {

    /** A PDF with less text than this is treated as scanned and sent to the model as a PDF. */
    static final int MIN_PDF_TEXT_CHARS = 40;

    public record UploadedFile(DocumentCategory category, MultipartFile file) {
    }

    private final AiProperties props;

    public DocumentExtractor(AiProperties props) {
        this.props = props;
    }

    public List<SourceDocument> extract(List<UploadedFile> uploads) {
        if (uploads.size() > props.maxFiles()) {
            throw invalid("Upload at most " + props.maxFiles() + " files.", "too_many_files");
        }
        List<SourceDocument> documents = new ArrayList<>();
        for (UploadedFile upload : uploads) {
            String id = "D" + (documents.size() + 1);
            documents.add(extractOne(id, upload, documents.size()));
        }
        return applyTextBudget(documents);
    }

    private SourceDocument extractOne(String id, UploadedFile upload, int index) {
        MultipartFile file = upload.file();
        String name = displayName(file.getOriginalFilename(), index);
        if (file.isEmpty()) {
            throw invalid(name + " is empty.", "empty_file");
        }
        if (file.getSize() > props.maxFileSize().toBytes()) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE,
                    name + " is larger than " + props.maxFileSize().toMegabytes() + " MB.", "file_too_large");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw invalid(name + " could not be read.", "unreadable_upload");
        }

        return switch (detect(bytes)) {
            case PDF -> extractPdf(id, name, upload.category(), bytes);
            case PNG -> image(id, name, upload.category(), bytes, "image/png");
            case JPEG -> image(id, name, upload.category(), bytes, "image/jpeg");
            case TEXT -> new SourceDocument(id, name, upload.category(), SourceDocument.Kind.TEXT,
                    new String(bytes, StandardCharsets.UTF_8).strip(), null, "text/plain", false);
            case UNSUPPORTED -> throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    name + " is not supported. Upload PDF, PNG, JPEG or TXT files.", "unsupported_type");
        };
    }

    private SourceDocument extractPdf(String id, String name, DocumentCategory category, byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() > props.maxPdfPages()) {
                throw invalid(name + " has more than " + props.maxPdfPages() + " pages.", "pdf_too_many_pages");
            }
            String text = new PDFTextStripper().getText(pdf).strip();
            if (text.length() < MIN_PDF_TEXT_CHARS) {
                // Likely a scan: let the model read the pages visually.
                return new SourceDocument(id, name, category, SourceDocument.Kind.PDF_SCANNED, null, bytes,
                        "application/pdf", false);
            }
            return new SourceDocument(id, name, category, SourceDocument.Kind.PDF_TEXT, text, null,
                    "application/pdf", false);
        } catch (InvalidPasswordException e) {
            throw invalid(name + " is password-protected.", "pdf_encrypted");
        } catch (IOException e) {
            throw invalid(name + " could not be read as a PDF.", "pdf_unreadable");
        }
    }

    private static SourceDocument image(String id, String name, DocumentCategory category, byte[] bytes,
            String mimeType) {
        return new SourceDocument(id, name, category, SourceDocument.Kind.IMAGE, null, bytes, mimeType, false);
    }

    /** Caps the total extracted text so prompts stay fast and cheap; later documents get what is left. */
    private List<SourceDocument> applyTextBudget(List<SourceDocument> documents) {
        int remaining = props.maxDocumentChars();
        List<SourceDocument> result = new ArrayList<>();
        for (SourceDocument doc : documents) {
            if (!doc.hasText()) {
                result.add(doc);
                continue;
            }
            if (doc.text().length() <= remaining) {
                remaining -= doc.text().length();
                result.add(doc);
            } else {
                result.add(doc.withText(doc.text().substring(0, remaining), true));
                remaining = 0;
            }
        }
        return result;
    }

    enum FileType {
        PDF, PNG, JPEG, TEXT, UNSUPPORTED
    }

    static FileType detect(byte[] b) {
        if (startsWith(b, '%', 'P', 'D', 'F', '-')) {
            return FileType.PDF;
        }
        if (startsWith(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return FileType.PNG;
        }
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
            return FileType.JPEG;
        }
        return isPlainText(b) ? FileType.TEXT : FileType.UNSUPPORTED;
    }

    /** Valid UTF-8 with no NUL bytes; rejects Office files, archives and other binaries. */
    private static boolean isPlainText(byte[] b) {
        for (byte x : b) {
            if (x == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        if (b.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((b[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** File name safe to show and to embed in the prompt: no path, no markup characters. */
    static String displayName(String original, int index) {
        String name = original == null ? "" : original.replaceAll(".*[/\\\\]", "");
        name = name.replaceAll("[\\p{Cntrl}<>\"&]", "").strip();
        if (name.isEmpty()) {
            name = "document-" + (index + 1);
        }
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static ApiException invalid(String message, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, reason);
    }
}
