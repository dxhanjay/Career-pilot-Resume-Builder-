package com.careerpilot.parsing.domain.extract;

/**
 * What kind of thing a skill is.
 *
 * <p>Category is not decoration. Job matching weights a missing programming
 * language differently from a missing soft skill — "we require Java and you do
 * not have it" is disqualifying, "we value communication" is not — and the
 * learning roadmap orders topics by category before difficulty.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum SkillCategory {

    /** Programming and query languages. */
    LANGUAGE,

    /** Frameworks, libraries, and runtimes. */
    FRAMEWORK,

    /** Databases and data stores. */
    DATABASE,

    /** Cloud platforms, containers, CI/CD, and infrastructure. */
    CLOUD_DEVOPS,

    /** Editors, trackers, and other tooling. */
    TOOL,

    /** Methods and bodies of knowledge — algorithms, REST, machine learning. */
    CONCEPT,

    /** Interpersonal and working skills. */
    SOFT_SKILL
}
