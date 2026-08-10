package com.careerpilot.parsing.domain;

import com.careerpilot.parsing.domain.extract.ContactDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The contact block extracted from one parse.
 *
 * <p>The most sensitive row the system stores: a real person's name, address,
 * and phone number, keyed to their account. It exists because
 * {@code FR-PARSE-03} requires showing candidates what a screener reads from
 * their header — a resume whose contact details sit in a text box extracts to
 * nothing here, and that silence is the finding.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "parsed_contacts")
public class ParsedContact {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "parse_id", nullable = false, updatable = false)
    private UUID parseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(name = "confidence", nullable = false)
    private short confidence;

    @Column(name = "name_confidence")
    private Short nameConfidence;

    @Column(name = "source_line_start")
    private Integer sourceLineStart;

    @Column(name = "source_line_end")
    private Integer sourceLineEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected ParsedContact() {
    }

    /**
     * Builds a persistable row from an extraction result.
     *
     * @param parseId the parse this belongs to
     * @param userId  the owner
     * @param details what the extractor found
     * @return the entity
     */
    public static ParsedContact from(UUID parseId, UUID userId, ContactDetails details) {
        ParsedContact contact = new ParsedContact();
        contact.parseId = parseId;
        contact.userId = userId;
        contact.fullName = details.fullName();
        contact.email = details.email();
        contact.phone = details.phone();
        contact.location = details.location();
        contact.linkedinUrl = details.linkedinUrl();
        contact.githubUrl = details.githubUrl();
        contact.portfolioUrl = details.portfolioUrl();
        contact.confidence = (short) details.confidence();
        contact.nameConfidence = details.nameConfidence() == null
                ? null
                : details.nameConfidence().shortValue();
        contact.sourceLineStart = details.lineStart() < 0 ? null : details.lineStart();
        contact.sourceLineEnd = details.lineEnd() < 0 ? null : details.lineEnd();
        return contact;
    }

    // --- accessors ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getParseId() {
        return parseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getLocation() {
        return location;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public short getConfidence() {
        return confidence;
    }

    public Short getNameConfidence() {
        return nameConfidence;
    }

    public Integer getSourceLineStart() {
        return sourceLineStart;
    }

    public Integer getSourceLineEnd() {
        return sourceLineEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParsedContact contact)) {
            return false;
        }
        return id != null && id.equals(contact.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ParsedContact.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Reports only which fields were populated, never their values. Every
     * field on this entity is personal data, and a {@code toString()} reaches
     * log lines and exception messages, both of which leave the process.
     */
    @Override
    public String toString() {
        return "ParsedContact{id=" + id
                + ", name=" + (fullName != null)
                + ", email=" + (email != null)
                + ", phone=" + (phone != null)
                + ", confidence=" + confidence + "}";
    }
}
