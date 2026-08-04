package com.careerpilot.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture rules, expressed as tests that fail the build.
 *
 * <p><strong>This is the most consequential file in Phase 2.</strong> Every
 * project claims to follow Clean Architecture in its README. The claim survives
 * contact with a deadline only when it is mechanically checked. The first time
 * someone injects a repository into a controller because it is 6pm and it
 * works, the convention is gone — and it is gone permanently, because the next
 * person copies the file that already exists.
 *
 * <p>These rules turn "we don't do that" into a red build. A reviewer can miss
 * a violation; a failing test cannot be missed.
 *
 * <p><strong>Why plain {@code @Test} methods rather than ArchUnit's
 * {@code @ArchTest} JUnit engine.</strong> The engine form was tried first and
 * silently discovered none of the field-declared rules against the JUnit
 * Platform version Spring Boot 3.5 ships — the suite reported "Tests run: 1" and
 * passed while enforcing nothing. A class violating two rules built cleanly.
 * That is the worst possible failure mode for a safety net: green, silent, and
 * useless.
 *
 * <p>Plain {@code @Test} methods invoking {@code rule.check(CLASSES)} remove
 * the dependency on a third-party engine's discovery working correctly with
 * whatever JUnit Platform version arrives next. Every rule appears by name in
 * the Surefire report, so "did this run?" is answerable by reading the output
 * rather than by trusting it.
 *
 * <p><strong>Why the rules exist before the code they govern.</strong> Phase 2
 * has no domain classes, no controllers, and no entities, so most of these rules
 * currently match nothing. That is intentional: the very first controller
 * written in Phase 3 is checked at the moment it is written, rather than audited
 * later against a standard nobody remembers agreeing to.
 * {@code src/test/resources/archunit.properties} disables ArchUnit's
 * fail-on-empty default so an as-yet-unwritten layer does not fail the build,
 * and {@link #import_is_not_empty()} guards the one dangerous case that
 * suppression could hide.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("Architecture rules")
class ArchitectureTest {

    /**
     * Imported once for the whole class. Scanning the compiled classes takes
     * roughly a second, and repeating it per rule would turn a fast test class
     * into a slow one for no benefit.
     */
    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.careerpilot");
    }

    // =======================================================================
    // Guard against vacuous success
    // =======================================================================

    /**
     * Fails if the import found no classes at all.
     *
     * <p>{@code failOnEmptyShould} is disabled globally so that rules may
     * legitimately precede the code they govern. That suppression hides one
     * genuinely dangerous case: if the base package is renamed and this file is
     * not updated, every rule below passes trivially and the build stays green
     * while enforcing nothing.
     *
     * <p>This check restores the floor. Individual rules may match nothing; the
     * import as a whole may not.
     */
    @Test
    @DisplayName("ArchUnit is actually analysing classes")
    void import_is_not_empty() {
        assertThat(classes)
                .as("""
                        ArchUnit imported zero classes from com.careerpilot. Every rule in \
                        this file would pass vacuously. Check the importPackages() argument.""")
                .isNotEmpty();
    }

    // =======================================================================
    // Layering — the dependency rule points inward
    // =======================================================================

    /**
     * The domain layer must not depend on Spring, infrastructure, or vendor SDKs.
     *
     * <p>This is the rule the whole structure rests on. It is what makes the
     * scoring rubric, the matching logic, and the fabrication guard testable
     * with a plain JUnit test — no Spring context, no database, no API key, and
     * a runtime measured in milliseconds. Logic that can only be tested through
     * a live model call is logic that does not get tested.
     *
     * <p>Jakarta Persistence is deliberately absent from this list, and that is
     * the one compromise recorded in the folder-structure document: entities
     * carry JPA annotations rather than being mapped to a separate persistence
     * model. Maintaining that mapping layer is a permanent tax on a two-person
     * team for a benefit that only pays out on a database change we are not
     * going to make. The dependency <em>direction</em> stays honest; the
     * annotation coupling is accepted openly rather than pretended away.
     */
    @Test
    @DisplayName("domain must not depend on frameworks or infrastructure")
    void domain_must_not_depend_on_frameworks() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "..infrastructure..",
                        "..api..",
                        "com.cloudinary..",
                        "org.apache.pdfbox..",
                        "org.apache.tika..")
                .because("""
                        The domain layer holds business rules and must stay testable without \
                        a Spring context, a database, or a network. A framework import here \
                        is how that property is lost.""")
                .check(classes);
    }

    /**
     * Controllers must not reach past the service layer into repositories or
     * adapters.
     *
     * <p>A controller calling a repository directly bypasses the transaction
     * boundary, the authorisation check, and the business rules — all of which
     * live in the service. It also means the same operation behaves differently
     * depending on which entry point invoked it.
     */
    @Test
    @DisplayName("api must not depend on infrastructure")
    void api_must_not_depend_on_infrastructure() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("""
                        Controllers must go through the application layer. Reaching directly \
                        into a repository skips the transaction boundary and the ownership \
                        check.""")
                .check(classes);
    }

    /**
     * The application layer must not depend on HTTP types.
     *
     * <p>A service that accepts a {@code HttpServletRequest} or returns a
     * {@code ResponseEntity} can only ever be called from a controller. It
     * cannot be reused by a scheduled job, a background worker, or a test
     * without constructing fake HTTP objects. Keeping the service layer free of
     * web types is what lets the Phase 5 job engine invoke exactly the same use
     * cases the controllers do.
     */
    @Test
    @DisplayName("application must not depend on the web layer")
    void application_must_not_depend_on_web_layer() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.springframework.web..",
                        "org.springframework.http..")
                .because("""
                        Use cases are invoked by controllers and by background jobs alike. \
                        Coupling them to HTTP makes the second impossible.""")
                .check(classes);
    }

    // =======================================================================
    // NFR-SEC-05 — entities must never cross the HTTP boundary
    // =======================================================================

    /**
     * No class in the web layer may touch a JPA entity.
     *
     * <p>"Never expose entities, always use DTOs" appears in every code-review
     * checklist and is violated in every codebase that only writes it down. The
     * consequences are concrete: a password hash serialised because someone
     * returned a {@code User}; a lazy association triggering a query during
     * serialisation; an incoming request body binding directly to an entity and
     * letting a caller set a field the API never intended to expose.
     *
     * <p>Expressed as "the API layer may not depend on entities at all", which
     * is stronger than "must not return one" and considerably harder to work
     * around by accident.
     */
    @Test
    @DisplayName("api must not touch JPA entities (NFR-SEC-05)")
    void api_must_not_touch_entities() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity.class)
                .because("""
                        NFR-SEC-05. Entities carry fields the API must never expose and lazy \
                        associations that must never be serialised. Map to a DTO in the \
                        controller.""")
                .check(classes);
    }

    // =======================================================================
    // Injection style
    // =======================================================================

    /**
     * No field injection, anywhere.
     *
     * <p>Three concrete problems, not a style preference. A field-injected class
     * cannot be constructed in a unit test without reflection or a Spring
     * context. Its dependencies are invisible in the constructor signature, so
     * a class that has quietly acquired eight collaborators looks the same as
     * one with two. And it can be instantiated in an invalid state, with fields
     * still null, which turns a wiring error into a {@code NullPointerException}
     * at first use rather than a clear failure at startup.
     */
    @Test
    @DisplayName("no field injection")
    void no_field_injection() {
        noFields()
                .should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                .because("""
                        Use constructor injection. Field injection hides dependencies, \
                        permits invalid construction, and cannot be unit-tested without \
                        reflection.""")
                .check(classes);
    }

    // =======================================================================
    // Naming and placement
    // =======================================================================

    /**
     * Classes annotated {@code @RestController} must live in an {@code api}
     * package and be named {@code *Controller}.
     *
     * <p>Placement conventions decay silently. A controller in the wrong package
     * escapes every package-scoped rule above, so this rule is what keeps the
     * others meaningful.
     */
    @Test
    @DisplayName("controllers are named and placed correctly")
    void controllers_must_be_named_and_placed_correctly() {
        classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAPackage("..api..")
                .andShould().haveSimpleNameEndingWith("Controller")
                .because("""
                        A controller outside ..api.. escapes every layering rule that targets \
                        that package.""")
                .check(classes);
    }

    /**
     * Repositories belong in {@code infrastructure} and are named
     * {@code *Repository}.
     */
    @Test
    @DisplayName("repositories are named and placed correctly")
    void repositories_must_be_named_and_placed_correctly() {
        classes()
                .that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .should().resideInAPackage("..infrastructure..")
                .andShould().haveSimpleNameEndingWith("Repository")
                .because("Persistence is an infrastructure concern.")
                .check(classes);
    }

    /**
     * Classes annotated {@code @Service} live in the application layer and are
     * named {@code *Service}.
     *
     * <p>The placement half matters more than the naming half: a {@code @Service}
     * sitting in {@code infrastructure} would sidestep the rule forbidding the
     * application layer from importing web types, and would quietly become a
     * place for business logic to accumulate outside the layer that is supposed
     * to hold it.
     */
    @Test
    @DisplayName("services are named and placed correctly")
    void services_must_be_named_and_placed_correctly() {
        classes()
                .that().areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should().resideInAPackage("..application..")
                .andShould().haveSimpleNameEndingWith("Service")
                .because("Use cases belong in the application layer.")
                .check(classes);
    }

    // =======================================================================
    // Hygiene
    // =======================================================================

    /**
     * Nothing may print to standard output or standard error.
     *
     * <p>A {@code System.out.println} left in production code has no level, no
     * timestamp, no correlation ID, and no logger name — so it is invisible to
     * log filtering and useless for diagnosis. It also bypasses the structured
     * JSON format configured for production, producing a line the log collector
     * cannot parse.
     */
    @Test
    @DisplayName("nothing writes to standard streams")
    void no_console_printing() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .because("""
                        Use SLF4J. A println carries no level, no timestamp, and no \
                        correlation ID, and breaks structured logging in production.""")
                .check(classes);
    }

    /**
     * No use of {@code java.util.Date} or {@code java.util.Calendar}.
     *
     * <p>Both are mutable, zone-ambiguous, and long superseded. The database
     * design specifies {@code TIMESTAMPTZ} throughout; the Java side must be
     * {@code java.time} throughout to match, or the boundary between them
     * becomes a source of daylight-saving bugs that only appear twice a year.
     */
    @Test
    @DisplayName("no legacy java.util date API")
    void no_legacy_date_api() {
        noClasses()
                .should().dependOnClassesThat()
                .haveNameMatching("java\\.util\\.(Date|Calendar)")
                .because("""
                        Use java.time. The legacy types are mutable and carry no zone, which \
                        does not survive contact with TIMESTAMPTZ columns.""")
                .check(classes);
    }

    /**
     * Utility classes must not carry instance state.
     *
     * <p>Guards against the common drift where a "Utils" class gradually
     * acquires injected collaborators and mutable fields, becoming a service in
     * everything but name — and one with no transaction boundary and no test.
     */
    @Test
    @DisplayName("utility classes hold no instance state")
    void utility_classes_must_have_only_static_members() {
        fields()
                .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Utils")
                .should().beStatic()
                .because("A utility class with instance state is a service without a name.")
                .check(classes);
    }
}
