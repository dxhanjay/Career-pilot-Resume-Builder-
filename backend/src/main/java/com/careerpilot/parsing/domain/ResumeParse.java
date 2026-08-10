package com.careerpilot.parsing.domain;

import com.careerpilot.parsing.domain.section.LineModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One attempt at extracting text from a resume.
 *
 * <p>A row per <em>attempt</em>, not per resume. When PDFBox fails and Tika
 * succeeds, both are recorded. Failed attempts are the only evidence of which
 * real-world layouts defeat the parser, and discarding them discards exactly the
 * data needed to improve it.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "resume_parses")
public class ResumeParse {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resume_id", nullable = false, updatable = false)
    private UUID resumeId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "parser_name", nullable = false, length = 50)
    private String parserName;

    @Column(name = "parser_version", nullable = false, length = 20)
    private String parserVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParseStatus status;

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Column(name = "page_count")
    private Short pageCount;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "char_count")
    private Integer charCount;

    /**
     * Structural warnings, stored as JSONB.
     *
     * <p>Correct use of JSONB by the policy in the database design: warnings are
     * only ever read back whole and rendered. Nothing filters or aggregates on
     * them, so real columns would buy nothing and cost a migration each time a
     * warning type is added.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false, columnDefinition = "jsonb")
    private List<ParseWarning> warnings = List.of();

    /**
     * The line-normalisation contract in force when this parse was written.
     *
     * <p>Line pointers on the {@code parsed_*} rows hanging off this parse are
     * only valid against this version. Recorded here rather than on every entity
     * because it is a property of how this parse's {@code rawText} is split, not
     * of any individual entity found in it.
     */
    @Column(name = "normalisation_version")
    private Short normalisationVersion;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected ResumeParse() {
    }

    private ResumeParse(UUID resumeId, UUID userId, String parserName, String parserVersion,
                        ParseStatus status) {
        this.resumeId = resumeId;
        this.userId = userId;
        this.parserName = parserName;
        this.parserVersion = parserVersion;
        this.status = status;
    }

    /**
     * Records a successful extraction.
     *
     * @param resumeId      the resume
     * @param userId        the owner
     * @param parserVersion the extractor's library version
     * @param extracted     what the extractor produced
     * @param warnings      structural warnings, including content-quality ones
     * @param durationMs    how long extraction took
     * @return a persistable success record
     */
    public static ResumeParse succeeded(UUID resumeId, UUID userId, String parserVersion,
                                        ExtractedText extracted, List<ParseWarning> warnings,
                                        int durationMs) {
        ResumeParse parse = new ResumeParse(
                resumeId, userId, extracted.parser(), parserVersion, ParseStatus.SUCCEEDED);
        parse.rawText = extracted.rawText();
        parse.pageCount = (short) extracted.pageCount();
        parse.wordCount = extracted.wordCount();
        parse.charCount = extracted.charCount();
        parse.warnings = List.copyOf(warnings);
        parse.durationMs = durationMs;
        parse.normalisationVersion = (short) LineModel.NORMALISATION_VERSION;
        return parse;
    }

    /**
     * Records a failed extraction.
     *
     * <p>Persisted rather than discarded: this row is what tells us which
     * layouts defeat the parser.
     *
     * @param resumeId      the resume
     * @param userId        the owner
     * @param parserName    which extractor failed
     * @param parserVersion its version
     * @param errorMessage  why
     * @param durationMs    how long it took to fail
     * @return a persistable failure record
     */
    public static ResumeParse failed(UUID resumeId, UUID userId, String parserName,
                                     String parserVersion, String errorMessage, int durationMs) {
        ResumeParse parse = new ResumeParse(
                resumeId, userId, parserName, parserVersion, ParseStatus.FAILED);
        parse.errorMessage = errorMessage;
        parse.durationMs = durationMs;
        parse.warnings = List.of();
        return parse;
    }

    // --- accessors ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getParserName() {
        return parserName;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public ParseStatus getStatus() {
        return status;
    }

    public String getRawText() {
        return rawText;
    }

    public Short getPageCount() {
        return pageCount;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public List<ParseWarning> getWarnings() {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }

    public Short getNormalisationVersion() {
        return normalisationVersion;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResumeParse parse)) {
            return false;
        }
        return id != null && id.equals(parse.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ResumeParse.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Never includes {@code rawText}. That field is the full content of a
     * person's resume — name, address, phone number, employment history — and a
     * {@code toString()} ends up in log lines and exception messages, both of
     * which leave the process.
     */
    @Override
    public String toString() {
        return "ResumeParse{id=" + id + ", parser=" + parserName + ", status=" + status
                + ", words=" + wordCount + "}";
    }
}
