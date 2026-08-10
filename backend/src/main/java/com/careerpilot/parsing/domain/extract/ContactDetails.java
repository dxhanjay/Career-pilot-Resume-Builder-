package com.careerpilot.parsing.domain.extract;

/**
 * The contact block extracted from a resume.
 *
 * <p>Two confidence values, because the fields divide sharply. An email address
 * is located by an unambiguous pattern — it is either there or it is not. A name
 * is inferred from position and capitalisation, and is the one field here that
 * is genuinely a guess: a resume opening with a job title, a letterhead, or a
 * two-column header can defeat it. Averaging a certainty and a guess into one
 * number would describe neither.
 *
 * @param fullName        the candidate's name, or {@code null}
 * @param nameConfidence  0–100 for {@code fullName}, {@code null} when no name was found
 * @param email           first email address found, or {@code null}
 * @param phone           first phone number found, or {@code null}
 * @param location        city and region if confidently identified, else {@code null}
 * @param linkedinUrl     LinkedIn profile, or {@code null}
 * @param githubUrl       GitHub profile, or {@code null}
 * @param portfolioUrl    any other personal link, or {@code null}
 * @param confidence      0–100 that this block really is the contact block
 * @param lineStart       first line of the block
 * @param lineEnd         last line of the block
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record ContactDetails(
        String fullName,
        Integer nameConfidence,
        String email,
        String phone,
        String location,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        int confidence,
        int lineStart,
        int lineEnd
) {

    /** An empty result, for a document with no contact block at all. */
    public static ContactDetails none() {
        return new ContactDetails(null, null, null, null, null, null, null, null, 0, -1, -1);
    }

    /**
     * Whether anything at all was found.
     *
     * <p>An empty contact block is worth reporting to the user rather than
     * hiding: a resume whose header sits in a text box extracts to nothing here,
     * and an ATS will read it exactly as badly.
     *
     * @return {@code true} if no field was populated
     */
    public boolean isEmpty() {
        return fullName == null && email == null && phone == null && location == null
                && linkedinUrl == null && githubUrl == null && portfolioUrl == null;
    }

    /**
     * Whether a screener could actually contact this candidate.
     *
     * @return {@code true} if an email or phone number was found
     */
    public boolean isReachable() {
        return email != null || phone != null;
    }
}
