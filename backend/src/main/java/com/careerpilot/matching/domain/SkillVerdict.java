package com.careerpilot.matching.domain;

/**
 * What happened to one skill when the two documents were compared.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum SkillVerdict {

    /** The posting asked for it and the resume has it. */
    MATCHED,

    /** The posting asked for it and the resume does not show it. */
    MISSING,

    /**
     * The resume has it and the posting never mentions it.
     *
     * <p>Kept rather than discarded: a long list of unasked-for skills is
     * usually a sign the resume is aimed at a different role than the one being
     * applied for, and that is worth saying out loud.
     */
    EXTRA
}
