package com.careerpilot.parsing.application;

import com.careerpilot.parsing.domain.ParsedContact;
import com.careerpilot.parsing.domain.ParsedEducation;
import com.careerpilot.parsing.domain.ParsedExperience;
import com.careerpilot.parsing.domain.ParsedSkill;
import com.careerpilot.parsing.domain.extract.ContactDetails;
import com.careerpilot.parsing.domain.extract.ContactExtractor;
import com.careerpilot.parsing.domain.extract.DetectedSkill;
import com.careerpilot.parsing.domain.extract.EducationEntry;
import com.careerpilot.parsing.domain.extract.EducationExtractor;
import com.careerpilot.parsing.domain.extract.ExperienceEntry;
import com.careerpilot.parsing.domain.extract.ExperienceExtractor;
import com.careerpilot.parsing.domain.extract.SkillExtractor;
import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.careerpilot.parsing.domain.section.SectionSegmenter;
import com.careerpilot.parsing.infrastructure.ParsedContactRepository;
import com.careerpilot.parsing.infrastructure.ParsedEducationRepository;
import com.careerpilot.parsing.infrastructure.ParsedExperienceRepository;
import com.careerpilot.parsing.infrastructure.ParsedSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the Phase 6b extraction cascade over a successful parse and persists
 * what it found.
 *
 * <p>Segmentation and extraction are pure functions of the raw text, so this
 * class is a thin adapter: it decides nothing, it only stores. That matters for
 * two reasons — re-running it on the same text produces the same rows, and every
 * extraction rule stays unit-testable without a database.
 *
 * <p>Extraction failure is never fatal. A resume whose text was extracted but
 * whose sections could not be identified is still worth showing to the user: the
 * raw text is the evidence, and a partial structure beats a failed parse.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class StructuredParseWriter {

    private static final Logger log = LoggerFactory.getLogger(StructuredParseWriter.class);

    /**
     * A resume listing two hundred "skills" is keyword-stuffed, not skilled.
     * Persisting the whole list would also let it dominate every later match.
     */
    private static final int MAX_SKILLS = 120;

    private final ParsedContactRepository contactRepository;
    private final ParsedSkillRepository skillRepository;
    private final ParsedEducationRepository educationRepository;
    private final ParsedExperienceRepository experienceRepository;

    public StructuredParseWriter(ParsedContactRepository contactRepository,
                                 ParsedSkillRepository skillRepository,
                                 ParsedEducationRepository educationRepository,
                                 ParsedExperienceRepository experienceRepository) {
        this.contactRepository = contactRepository;
        this.skillRepository = skillRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
    }

    /**
     * Extracts and stores contact, skills, education and experience for a parse.
     *
     * <p>Idempotent: existing rows for the parse are removed first, so a re-run
     * replaces rather than duplicates.
     *
     * @param parseId the parse the rows belong to
     * @param userId  the owner, denormalised onto every row for tenant isolation
     * @param rawText the extracted text, exactly as stored on the parse
     * @return a count of what was written
     */
    public Summary write(UUID parseId, UUID userId, String rawText) {
        LineModel model = LineModel.of(rawText);
        if (model.isEmpty()) {
            return Summary.empty();
        }

        List<ResumeSection> sections = SectionSegmenter.segment(model);

        contactRepository.deleteByParseId(parseId);
        skillRepository.deleteByParseId(parseId);
        educationRepository.deleteByParseId(parseId);
        experienceRepository.deleteByParseId(parseId);

        int contacts = writeContact(parseId, userId, model, sections);
        int skills = writeSkills(parseId, userId, model, sections);
        int education = writeEducation(parseId, userId, model, sections);
        int experience = writeExperience(parseId, userId, model, sections);

        log.info("Structured parse {}: {} section(s), contact={}, {} skill(s), "
                        + "{} education, {} experience",
                parseId, sections.size(), contacts == 1, skills, education, experience);

        return new Summary(sections.size(), contacts, skills, education, experience);
    }

    private int writeContact(UUID parseId, UUID userId, LineModel model,
                             List<ResumeSection> sections) {
        ContactDetails details = ContactExtractor.extract(model, sections);
        if (details == null || details.isEmpty()) {
            return 0;
        }
        contactRepository.save(ParsedContact.from(parseId, userId, details));
        return 1;
    }

    private int writeSkills(UUID parseId, UUID userId, LineModel model,
                            List<ResumeSection> sections) {
        List<DetectedSkill> detected = SkillExtractor.extract(model, sections);
        if (detected.isEmpty()) {
            return 0;
        }
        // uq_parsed_skills_parse_name is a constraint, not a suggestion: the
        // extractor can legitimately see "React" in both the skills block and a
        // project bullet, and the second insert would abort the transaction.
        Set<String> seen = new LinkedHashSet<>();
        List<ParsedSkill> rows = new ArrayList<>();
        for (DetectedSkill skill : detected) {
            if (rows.size() >= MAX_SKILLS) {
                break;
            }
            if (seen.add(skill.normalizedName())) {
                rows.add(ParsedSkill.from(parseId, userId, skill));
            }
        }
        skillRepository.saveAll(rows);
        return rows.size();
    }

    private int writeEducation(UUID parseId, UUID userId, LineModel model,
                               List<ResumeSection> sections) {
        List<EducationEntry> entries = EducationExtractor.extract(model, sections).stream()
                .filter(entry -> !entry.isEmpty())
                .toList();
        educationRepository.saveAll(
                entries.stream().map(e -> ParsedEducation.from(parseId, userId, e)).toList());
        return entries.size();
    }

    private int writeExperience(UUID parseId, UUID userId, LineModel model,
                                List<ResumeSection> sections) {
        List<ExperienceEntry> entries = ExperienceExtractor.extract(model, sections).stream()
                .filter(entry -> !entry.isEmpty())
                .toList();
        experienceRepository.saveAll(
                entries.stream().map(e -> ParsedExperience.from(parseId, userId, e)).toList());
        return entries.size();
    }

    /**
     * How many rows of each kind a {@link #write} call produced.
     */
    public record Summary(int sections, int contacts, int skills, int education, int experience) {

        static Summary empty() {
            return new Summary(0, 0, 0, 0, 0);
        }

        public boolean foundNothing() {
            return contacts == 0 && skills == 0 && education == 0 && experience == 0;
        }
    }
}
