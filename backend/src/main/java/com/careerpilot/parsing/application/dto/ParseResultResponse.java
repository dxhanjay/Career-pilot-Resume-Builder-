package com.careerpilot.parsing.application.dto;

import com.careerpilot.parsing.domain.ParseWarning;
import com.careerpilot.parsing.domain.ResumeParse;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The outcome of extracting text from a resume.
 *
 * <p>{@code rawText} is omitted unless explicitly requested via
 * {@link #withText}. A resume's full text is several kilobytes of personal data
 * — name, phone number, address, employment history — and including it in every
 * status poll would ship all of that repeatedly to satisfy a question about
 * whether parsing had finished.
 *
 * <p>{@code warnings} is the field that carries the product's value. Almost
 * every competitor returns a score; showing a student that their two-column
 * layout scrambled their work history is what makes the score believable.
 *
 * @param parseId      identifier of this parse attempt
 * @param resumeId     the resume parsed
 * @param status       {@code SUCCEEDED} or {@code FAILED}
 * @param parser       which extractor produced this
 * @param parserVersion the extractor's library version
 * @param pageCount    pages processed, where the format reports it
 * @param wordCount    words extracted
 * @param charCount    characters extracted
 * @param durationMs   how long extraction took
 * @param warnings     structural and quality problems detected
 * @param error        why extraction failed, when it did
 * @param rawText      the extracted text; present only on the raw-text endpoint
 * @param createdAt    when the attempt ran
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ParseResultResponse", description = "Result of extracting text from a resume")
public record ParseResultResponse(
        UUID parseId,
        UUID resumeId,
        String status,
        String parser,
        String parserVersion,
        Short pageCount,
        Integer wordCount,
        Integer charCount,
        Integer durationMs,
        List<ParseWarning> warnings,
        String error,
        String rawText,
        Instant createdAt
) {

    /**
     * Summary view, without the extracted text.
     *
     * @param parse the entity
     * @return the response DTO
     */
    public static ParseResultResponse from(ResumeParse parse) {
        return build(parse, null);
    }

    /**
     * Full view, including the extracted text.
     *
     * <p>Used only by the raw-text endpoint, which is what backs the "here's
     * what the machine saw" screen.
     *
     * @param parse the entity
     * @return the response DTO with {@code rawText} populated
     */
    public static ParseResultResponse withText(ResumeParse parse) {
        return build(parse, parse.getRawText());
    }

    private static ParseResultResponse build(ResumeParse parse, String rawText) {
        return new ParseResultResponse(
                parse.getId(),
                parse.getResumeId(),
                parse.getStatus().name(),
                parse.getParserName(),
                parse.getParserVersion(),
                parse.getPageCount(),
                parse.getWordCount(),
                parse.getCharCount(),
                parse.getDurationMs(),
                parse.getWarnings(),
                parse.getErrorMessage(),
                rawText,
                parse.getCreatedAt());
    }
}
