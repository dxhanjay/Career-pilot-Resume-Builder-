package com.careerpilot.admin.application;

import com.careerpilot.auth.domain.Role;
import com.careerpilot.auth.domain.RoleName;
import com.careerpilot.auth.domain.User;
import com.careerpilot.auth.infrastructure.RoleRepository;
import com.careerpilot.auth.infrastructure.UserRepository;
import com.careerpilot.config.properties.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants {@code ROLE_ADMIN} to the accounts named in {@code app.admin.bootstrap-emails}.
 *
 * <p>Runs on every startup and is idempotent: an account that already has the
 * role is skipped, and an email with no account yet is simply logged, so the
 * intended administrator can register afterwards and be promoted on the next
 * deploy or restart.
 *
 * <p>Additive only. It never removes a role — see {@link AdminProperties} for
 * why demotion is deliberately not an environment-variable side effect.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AdminBootstrap(AdminProperties properties,
                          UserRepository userRepository,
                          RoleRepository roleRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.bootstrapEmails().isEmpty()) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElse(null);
        if (adminRole == null) {
            // V2 seeds this row. Its absence means migrations did not run, which
            // is a far bigger problem than a missing promotion — say so and let
            // the rest of startup surface it.
            log.error("ROLE_ADMIN is missing from the roles table; cannot promote administrators");
            return;
        }

        for (String email : properties.bootstrapEmails()) {
            String trimmed = email == null ? "" : email.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            userRepository.findActiveByEmail(trimmed).ifPresentOrElse(
                    user -> promote(user, adminRole, trimmed),
                    () -> log.warn("Admin bootstrap: no account for {} yet. Register it, then "
                            + "restart to apply.", trimmed));
        }
    }

    private void promote(User user, Role adminRole, String email) {
        boolean alreadyAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);
        if (alreadyAdmin) {
            return;
        }
        user.addRole(adminRole);
        log.info("Admin bootstrap: granted ROLE_ADMIN to {}", email);
    }
}
