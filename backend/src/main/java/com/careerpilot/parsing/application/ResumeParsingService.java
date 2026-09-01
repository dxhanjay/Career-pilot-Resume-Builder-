package com.careerpilot.parsing.application;

import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.jobs.application.JobService;
import com.careerpilot.jobs.domain.Job;
import com.careerpilot.jobs.domain.JobType;
import com.careerpilot.parsing.application.dto.ParseResultResponse;
import com.careerpilot.parsing.application.dto.StructuredParseResponse;
import com.careerpilot.parsing.domain.ExtractedText;
import com.careerpilot.parsing.domain.ParseWarning;
import com.careerpilot.parsing.domain.ResumeParse;
import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.SectionSegmenter;
import com.careerpilot.parsing.infrastructure.ParsedContactRepository;
import com.careerpilot.parsing.infrastructure.ParsedEducationRepository;
import com.careerpilot.parsing.infrastructure.ParsedExperienceRepository;
import com.careerpilot.parsing.infrastructure.ParsedSkillRepository;
import com.careerpilot.parsing.infrastructure.ResumeParseRepository;
import com.careerpilot.resume.domain.Resume;
import com.careerpilot.resume.infrastructure.ResumeRepository;
import com.careerpilot.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extracts text from stored resumes.
 *
 * <p>The extraction strategy is "try each supporting extractor until one
 * produces usable text". PDFBox and Tika fail on different documents, so trying
 * both converts a class of hard failures into successes — at the cost of one
 * extra attempt on a file that was already failing.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class ResumeParsingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingService.class);

    private final ResumeRepository resumeRepository;
    private final ResumeParseRepository parseRepository;
    private final FileStorage fileStorage;
    private final JobService jobService;
    private final List<TextExtractor> extractors;
    private final StructuredParseWriter structuredParseWriter;
    private final ParsedContactRepository contactRepository;
    private final ParsedSkillRepository skillRepository;
    private final ParsedEducationRepository educationRepository;
    private final ParsedExperienceRepository experienceRepository;

    /**
     * @param extractors every {@link TextExtractor} Spring found, in
     *                   {@code @Order} sequence — PDFBox before Tika
     */
    public ResumeParsingService(ResumeRepository resumeRepository,
                                ResumeParseRepository parseRepository,
                                FileStorage fileStorage,
                                JobService jobService,
                                List<TextExtractor> extractors,
                                StructuredParseWriter structuredParseWriter,
                                ParsedContactRepository contactRepository,
                                ParsedSkillRepository skillRepository,
                                ParsedEducationRepository educationRepository,
                                ParsedExperienceRepository experienceRepository) {
        this.resumeRepository = resumeRepository;
        this.parseRepository = parseRepository;
        this.fileStorage = fileStorage;
        this.jobService = jobService;
        this.extractors = extractors;
        this.structuredParseWriter = structuredParseWriter;
        this.contactRepository = contactRepository;
        this.skillRepository = skillRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
    }

    /**
     * Enqueues a parse job.
     *
     * <p>Returns a job rather than a result. Extraction takes seconds and can
     * take longer on a cold container, which does not fit inside an HTTP request
     * — and on Railway that would surface as a timeout rather than a slow
     * response.
     *
     * @param resumeId the resume
     * @param userId   the owner
     * @return the enqueued job
     * @throws ResourceNotFoundException      if the resume is not theirs
     * @throws BusinessRuleViolationException if it is already being parsed
     */
    @Transactional
    public Job requestParse(UUID resumeId, UUID userId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume"));

        if (!resume.getStatus().canStartParsing()) {
            throw new BusinessRuleViolationException(
                    "This resume is already being parsed or has been parsed");
        }

        return jobService.enqueue(userId, JobType.PARSE_RESUME, resumeId);
    }

    /**
     * Performs extraction. Called by the job handler, not by a controller.
     *
     * <p>Each extractor that supports the format is tried in turn. An extractor
     * that throws, or that returns text too thin to be useful, moves on to the
     * next. Only when all are exhausted is the parse recorded as failed.
     *
     * <p>Every attempt is persisted, including the failures. Those rows are the
     * only record of which real-world layouts defeat the parser.
     *
     * @param resumeId the resume to parse
     * @return id of the successful {@link ResumeParse}
     * @throws ParseFailedException if no extractor produced usable text
     */
    @Transactional
    public UUID parse(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume"));

        resume.markParsing();

        byte[] content = fileStorage.retrieve(resume.getStoragePublicId());

        List<TextExtractor> applicable = extractors.stream()
                .filter(extractor -> extractor.supports(resume.getMimeType()))
                .toList();

        if (applicable.isEmpty()) {
            // Should be unreachable: upload validated the type from its bytes.
            // Reaching it means the accepted-format list and the extractor set
            // have drifted apart, which is a wiring bug rather than a bad file.
            resume.markParseFailed();
            throw new ParseFailedException(
                    "No extractor is configured for " + resume.getMimeType(), false);
        }

        String lastError = null;

        for (int i = 0; i < applicable.size(); i++) {
            TextExtractor extractor = applicable.get(i);
            boolean isFallback = i > 0;
            long startedAt = System.nanoTime();

            try {
                ExtractedText extracted = extractor.extract(content, resume.getMimeType());
                int durationMs = elapsedMs(startedAt);

                if (!extracted.isUsable()) {
                    // Read successfully, but there is nothing to analyse. Almost
                    // always a scan. Recorded as a failed attempt so the next
                    // extractor gets a turn - Tika occasionally recovers text
                    // from PDFs where PDFBox finds none.
                    lastError = "No usable text found";
                    parseRepository.save(ResumeParse.failed(
                            resumeId, resume.getUserId(), extractor.name(), extractor.version(),
                            lastError, durationMs));
                    log.info("{} extracted only {} words from resume {}",
                            extractor.name(), extracted.wordCount(), resumeId);
                    continue;
                }

                List<ParseWarning> warnings = new ArrayList<>(extracted.qualityWarnings());
                if (isFallback) {
                    warnings.add(ParseWarning.fallbackParserUsed(extractor.name()));
                }

                ResumeParse parse = parseRepository.save(ResumeParse.succeeded(
                        resumeId, resume.getUserId(), extractor.version(),
                        extracted, warnings, durationMs));

                resume.markParsed();

                // Structure extraction runs in the same transaction as the parse
                // it describes. A parse row whose parsed_* children are missing
                // looks identical to one whose resume genuinely had no
                // recognisable sections, and nothing downstream could tell the
                // difference.
                structuredParseWriter.write(
                        parse.getId(), resume.getUserId(), extracted.rawText());

                log.info("Parsed resume {} with {} in {}ms: {} words, {} warning(s)",
                        resumeId, extractor.name(), durationMs,
                        extracted.wordCount(), warnings.size());

                return parse.getId();

            } catch (TextExtractor.TextExtractionException e) {
                lastError = e.getMessage();
                parseRepository.save(ResumeParse.failed(
                        resumeId, resume.getUserId(), extractor.name(), extractor.version(),
                        lastError, elapsedMs(startedAt)));
                log.warn("{} failed on resume {}: {}", extractor.name(), resumeId, e.getMessage());
            }
        }

        resume.markParseFailed();

        // Permanent, not transient. The same bytes through the same extractors
        // will fail identically, so retrying costs time and money for an
        // outcome we already know - and delays telling the user something they
        // can act on.
        throw new ParseFailedException(
                lastError == null ? "Could not extract any text" : lastError, false);
    }

    /**
     * Returns the extraction result for a resume.
     *
     * @param resumeId the resume
     * @param userId   the requesting user
     * @return the latest successful parse, or the latest failure with its reason
     * @throws ResourceNotFoundException if nothing has been parsed yet
     */
    @Transactional(readOnly = true)
    public ParseResultResponse getResult(UUID resumeId, UUID userId) {
        return parseRepository.findLatestSuccessful(resumeId, userId)
                .or(() -> parseRepository.findLatestAttempt(resumeId, userId))
                .map(ParseResultResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Parse result"));
    }

    /**
     * Returns the raw extracted text.
     *
     * <p>The data behind the "here's what the machine saw" screen — the moment
     * that makes the product credible, because almost no competitor shows the
     * parse rather than just a score.
     *
     * @param resumeId the resume
     * @param userId   the requesting user
     * @return the extracted text
     * @throws ResourceNotFoundException      if nothing has been parsed
     * @throws BusinessRuleViolationException if the latest attempt failed
     */
    /**
     * The structured view of the latest successful parse: sections, contact,
     * skills, education and experience, each carrying the source lines it came
     * from.
     *
     * <p>Sections are recomputed from the stored text rather than persisted.
     * Segmentation is a pure function of the normalised text and the stored
     * {@code normalisation_version} pins that contract, so recomputation is
     * cheaper than a table and can never drift from the line pointers held on
     * the entity rows.
     *
     * @throws BusinessRuleViolationException if the resume has no successful parse
     */
    @Transactional(readOnly = true)
    public StructuredParseResponse getStructured(UUID resumeId, UUID userId) {
        ResumeParse parse = parseRepository.findLatestSuccessful(resumeId, userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "This resume has not been parsed successfully yet"));

        LineModel model = LineModel.of(parse.getRawText());

        return new StructuredParseResponse(
                parse.getId(),
                parse.getResumeId(),
                model.lines().stream().map(line -> line.text()).toList(),
                SectionSegmenter.segment(model).stream()
                        .map(StructuredParseResponse.Section::from).toList(),
                contactRepository.findByParseId(parse.getId())
                        .map(StructuredParseResponse.Contact::from).orElse(null),
                skillRepository.findByParseIdOrderByConfidenceDesc(parse.getId()).stream()
                        .map(StructuredParseResponse.Skill::from).toList(),
                educationRepository.findByParseIdOrderByEndDateDesc(parse.getId()).stream()
                        .map(StructuredParseResponse.Education::from).toList(),
                experienceRepository.findByParseIdOrderByStartDateDesc(parse.getId()).stream()
                        .map(StructuredParseResponse.Experience::from).toList());
    }

    @Transactional(readOnly = true)
    public ParseResultResponse getRawText(UUID resumeId, UUID userId) {
        ResumeParse parse = parseRepository.findLatestSuccessful(resumeId, userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "This resume has not been parsed successfully yet"));

        return ParseResultResponse.withText(parse);
    }

    private int elapsedMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000);
    }

    /**
     * Raised when no extractor could produce usable text.
     *
     * <p>Carries the transient flag so the job engine knows whether to retry.
     */
    public static class ParseFailedException extends RuntimeException {

        private final boolean transientFailure;

        public ParseFailedException(String message, boolean transientFailure) {
            super(message);
            this.transientFailure = transientFailure;
        }

        public boolean isTransient() {
            return transientFailure;
        }
    }
}
