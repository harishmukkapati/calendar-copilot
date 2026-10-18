package dev.calendarcopilot.ai;

import java.text.Normalizer;

import org.springframework.stereotype.Component;

import dev.calendarcopilot.ai.PlanDraft.Verification;

/**
 * Checks that a quoted requirement actually appears in the cited document.
 * Deterministic, cheap and explainable, unlike asking a second model to grade the first.
 * It catches fabricated quotes; it cannot prove the requirement was interpreted correctly.
 */
@Component
public class CitationVerifier {

    /** Shorter quotes match too easily to count as evidence. */
    static final int MIN_QUOTE_CHARS = 12;

    public Verification verify(String quote, SourceDocument document) {
        if (document == null) {
            return Verification.NOT_FOUND;
        }
        if (!document.hasText()) {
            return Verification.UNVERIFIABLE;
        }
        return containsQuote(normalize(document.text()), quote) ? Verification.VERIFIED : Verification.NOT_FOUND;
    }

    /**
     * Models sometimes elide the middle of a passage with "..."; each piece must then
     * appear in the document, in order.
     */
    static boolean containsQuote(String normalizedDocument, String quote) {
        if (quote == null) {
            return false;
        }
        String[] pieces = normalize(quote).split("\\s*(\\.\\.\\.|…)\\s*");
        int searchFrom = 0;
        int matchedChars = 0;
        for (String piece : pieces) {
            String p = piece.strip();
            if (p.isEmpty()) {
                continue;
            }
            int found = normalizedDocument.indexOf(p, searchFrom);
            if (found < 0) {
                return false;
            }
            searchFrom = found + p.length();
            matchedChars += p.length();
        }
        return matchedChars >= MIN_QUOTE_CHARS;
    }

    /** Ignores differences in case, whitespace, line-break hyphenation and typographic quotes/dashes. */
    static String normalize(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace("­", "");
        s = s.replaceAll("(\\w)-\\s*\\n\\s*(\\w)", "$1$2"); // "assign-\nment" -> "assignment"
        return s.replaceAll("\\s+", " ").strip().toLowerCase();
    }
}
