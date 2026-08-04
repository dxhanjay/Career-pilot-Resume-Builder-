package com.careerpilot.support;

import com.careerpilot.auth.domain.RefreshToken;
import com.careerpilot.auth.domain.Role;
import com.careerpilot.auth.domain.RoleName;
import com.careerpilot.auth.domain.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Builds domain objects for tests.
 *
 * <p>Exists because entity identifiers are assigned by Hibernate at persist
 * time, so an object constructed in a unit test has a {@code null} id. Any code
 * under test that reads the identifier — {@code JwtTokenProvider} building a
 * token subject, {@code TokenService} recording a rotation — would fail with a
 * {@code NullPointerException} that says nothing about the actual behaviour
 * being tested.
 *
 * <p>{@link ReflectionTestUtils} is the pragmatic answer. The alternative is an
 * id setter on the entity, which would exist solely for tests and would let
 * production code reassign a primary key. A test utility reaching in through
 * reflection keeps that capability out of the production API entirely, and is
 * confined to this one file rather than spread across every test.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class TestDataFactory {

    /** A valid BCrypt hash, so entities look realistic without hashing cost. */
    public static final String SAMPLE_BCRYPT_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.3Ug3BdOZ.4Gd0lPQ1kIYxDS6MZ8Cvfy";

    private TestDataFactory() {
    }

    /**
     * A verified, active user with {@code ROLE_USER} and a populated id.
     *
     * @param email the address
     * @return a ready-to-use user
     */
    public static User activeUser(String email) {
        User user = new User(email, SAMPLE_BCRYPT_HASH, "Test User");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.now());
        ReflectionTestUtils.setField(user, "updatedAt", Instant.now());
        user.addRole(userRole());
        user.verifyEmail();
        return user;
    }

    /**
     * An unverified user in {@code PENDING}, with a populated id.
     *
     * @param email the address
     * @return a user that has not confirmed its address
     */
    public static User pendingUser(String email) {
        User user = new User(email, SAMPLE_BCRYPT_HASH, "Pending User");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.now());
        ReflectionTestUtils.setField(user, "updatedAt", Instant.now());
        user.addRole(userRole());
        return user;
    }

    /**
     * @return the {@code ROLE_USER} reference row, with a populated id
     */
    public static Role userRole() {
        return role(RoleName.ROLE_USER);
    }

    /**
     * Builds a role.
     *
     * <p>Instantiated reflectively because {@link Role}'s no-argument
     * constructor is {@code protected} — it exists for JPA, not for application
     * code, and roles are reference data seeded by migration rather than
     * constructed at runtime. Widening that constructor purely to make tests
     * convenient would hand production code the ability to invent roles, which
     * is exactly what the visibility is there to prevent.
     *
     * @param name the role to build
     * @return a role with a populated id
     */
    public static Role role(RoleName name) {
        try {
            var constructor = Role.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Role role = constructor.newInstance();
            ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(role, "name", name);
            return role;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not build a test Role", e);
        }
    }

    /**
     * A usable refresh token with a populated id.
     *
     * @param user     the owner
     * @param hash     the stored token hash
     * @param familyId the rotation lineage
     * @return a token valid for seven days
     */
    public static RefreshToken refreshToken(User user, String hash, UUID familyId) {
        RefreshToken token = new RefreshToken(
                user, hash, familyId,
                Instant.now().plus(7, ChronoUnit.DAYS),
                "JUnit", "127.0.0.1");
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        return token;
    }

    /**
     * A refresh token that has already expired.
     *
     * @param user     the owner
     * @param hash     the stored token hash
     * @param familyId the rotation lineage
     * @return a token whose expiry is in the past
     */
    public static RefreshToken expiredRefreshToken(User user, String hash, UUID familyId) {
        RefreshToken token = new RefreshToken(
                user, hash, familyId,
                Instant.now().minus(1, ChronoUnit.DAYS),
                "JUnit", "127.0.0.1");
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        return token;
    }
}
