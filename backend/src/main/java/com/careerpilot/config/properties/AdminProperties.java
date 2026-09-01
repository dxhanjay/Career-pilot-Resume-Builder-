package com.careerpilot.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Which accounts are administrators.
 *
 * <p>Configuration rather than a database flag, and deliberately so. The first
 * administrator of a fresh deployment has to come from somewhere, and the two
 * alternatives are worse: a seeded account with a default password is a
 * well-known credential on every deployment that forgets to change it, and a
 * "first user to register becomes admin" rule is a race anyone who finds the URL
 * early can win.
 *
 * <p>Promotion is applied at startup and is additive. Removing an email here
 * does not demote anyone — revoking a role is an audited action, not a side
 * effect of an environment variable being edited.
 *
 * @param bootstrapEmails email addresses granted {@code ROLE_ADMIN} on startup,
 *                        matched case-insensitively; empty by default
 * @author CareerPilot AI
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(List<String> bootstrapEmails) {

    public AdminProperties {
        bootstrapEmails = bootstrapEmails == null ? List.of() : List.copyOf(bootstrapEmails);
    }
}
