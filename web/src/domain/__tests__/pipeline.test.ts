import { describe, expect, it } from 'vitest'
import { evaluateAts } from '../ats'
import { extractContact, extractEducation, extractExperience, extractSkills, parseDateRange } from '../extract'
import { evaluateAnswer, generateQuestions, hashSeed } from '../interview'
import { matchResume, parseJobPosting } from '../matching'
import { segmentSections } from '../sections'
import { impliedBy, skillLabel } from '../skills'
import type { ResumeSnapshot } from '../snapshot'
import { EMPTY_CONTACT } from '../snapshot'
import { indexOfToken, toLineModel } from '../text'

/**
 * The analysis pipeline, end to end and by part.
 *
 * Four of these are regressions for bugs found by running the deployed app
 * against a real resume rather than by reading the code — each is marked, and
 * each one produced advice that was confidently wrong, which is worse than
 * advice that is merely missing.
 */

const RESUME = `Aditi Sharma
aditi.sharma@example.com | +91 98765 43210 | Bengaluru, India
github.com/aditisharma

EXPERIENCE

Software Engineering Intern, Acme Technologies
Jun 2024 - Aug 2024
- Built a reporting service in Java and Spring Boot for 3 internal teams
- Reduced report generation time by 40% by batching PostgreSQL queries
- Responsible for testing the payment module before each release

EDUCATION

B.Tech Computer Science, National Institute of Technology
2021 - 2025

SKILLS

Java, Spring Boot, PostgreSQL, JavaScript, React, Git, Linux`

const POSTING = `Backend Engineering Intern
Globex Systems - Bengaluru

Requirements:
- Strong experience with Java and Spring Boot
- Proficient in SQL and PostgreSQL
- Solid understanding of Docker and containerised deployment
- Experience with Kubernetes in production

Nice to have:
- Exposure to Redis
- Familiarity with Kafka`

/** Builds the read model the way the application does, from raw text. */
function snapshotOf(text: string): ResumeSnapshot {
  const model = toLineModel(text)
  const sections = segmentSections(model)
  const contact = extractContact(model, sections)

  return {
    lines: model.lines.map((line) => line.text),
    sections: sections.map((section) => ({
      type: section.type,
      headingText: section.headingText,
      headingLine: section.headingLine,
      startLine: section.startLine,
      endLine: section.endLine,
      confidence: section.confidence,
    })),
    contact: {
      fullName: contact.fullName, email: contact.email, phone: contact.phone,
      location: contact.location, linkedinUrl: contact.linkedinUrl,
      githubUrl: contact.githubUrl, portfolioUrl: contact.portfolioUrl,
    },
    skills: extractSkills(model, sections).map((skill) => ({
      name: skill.name, normalizedName: skill.normalizedName,
      category: skill.category, confidence: skill.confidence, lineStart: skill.lineStart,
    })),
    education: extractEducation(model, sections).map((entry) => ({
      institution: entry.institution, degree: entry.degree,
      fieldOfStudy: entry.fieldOfStudy, startDate: entry.startDate,
      endDate: entry.endDate, lineStart: entry.lineStart, lineEnd: entry.lineEnd,
    })),
    experience: extractExperience(model, sections).map((entry) => ({
      company: entry.company, jobTitle: entry.jobTitle,
      startDate: entry.startDate, endDate: entry.endDate,
      isCurrent: entry.isCurrent, description: entry.description,
      lineStart: entry.lineStart, lineEnd: entry.lineEnd,
    })),
    warningCodes: new Set<string>(),
    pageCount: 1, wordCount: 77, charCount: 520,
  }
}

describe('token matching', () => {
  it('does not match a term inside a longer word', () => {
    // The bug this prevents: "java" matching inside "javascript", which makes
    // every JavaScript resume claim Java.
    expect(indexOfToken('i know javascript', 'java')).toBe(-1)
    expect(indexOfToken('i know java and go', 'java')).toBe(7)
    expect(indexOfToken('computer science', 'c')).toBe(-1)
  })

  it('handles terms containing punctuation', () => {
    expect(indexOfToken('built in c++ and node.js', 'c++')).toBeGreaterThan(-1)
    expect(indexOfToken('built in c++ and node.js', 'node.js')).toBeGreaterThan(-1)
  })
})

describe('sections', () => {
  it('finds the core blocks of an ordinary resume', () => {
    const sections = segmentSections(toLineModel(RESUME))
    expect(sections.map((section) => section.type)).toEqual([
      'CONTACT', 'EXPERIENCE', 'EDUCATION', 'SKILLS',
    ])
  })

  it('returns one unknown block when nothing looks like a heading', () => {
    const sections = segmentSections(toLineModel('just\nsome\nlines\nwith no headings'))
    expect(sections).toHaveLength(1)
    expect(sections[0].type).toBe('UNKNOWN')
  })
})

describe('dates', () => {
  it('reads a month-year range', () => {
    expect(parseDateRange('Jun 2024 - Aug 2024')).toEqual({
      start: '2024-06-01', end: '2024-08-01', current: false,
    })
  })

  it('reads an ongoing role', () => {
    expect(parseDateRange('Jan 2023 - Present')).toMatchObject({
      start: '2023-01-01', end: null, current: true,
    })
  })

  it('refuses rather than guessing at an implausible year', () => {
    // A wrong date silently corrupts years-of-experience, and a candidate told
    // they have four years when they have two gets caught out in the interview.
    expect(parseDateRange('call 3021 for details')).toBeNull()
    expect(parseDateRange('no dates here at all')).toBeNull()
  })
})

describe('extraction', () => {
  const snapshot = snapshotOf(RESUME)

  it('reads the contact block', () => {
    expect(snapshot.contact).toMatchObject({
      fullName: 'Aditi Sharma',
      email: 'aditi.sharma@example.com',
      location: 'Bengaluru, India',
    })
    expect(snapshot.contact.phone).toContain('98765')
  })

  it('REGRESSION: keeps a role and its date line as one entry', () => {
    // Found by running the deployed app. Treating every dated line as the start
    // of a new entry split "Title, Company" from "Jun 2024 - Aug 2024" into two
    // entries — so the resume was told it had an experience entry with no
    // readable dates, about the entry whose dates were in the phantom row
    // underneath it, and its years of experience computed as zero.
    expect(snapshot.experience).toHaveLength(1)
    expect(snapshot.experience[0]).toMatchObject({
      jobTitle: 'Software Engineering Intern',
      company: 'Acme Technologies',
      startDate: '2024-06-01',
      endDate: '2024-08-01',
    })
  })

  it('REGRESSION: keeps a qualification and its dates as one entry', () => {
    expect(snapshot.education).toHaveLength(1)
    expect(snapshot.education[0]).toMatchObject({ degree: 'B.Tech', endDate: '2025-12-01' })
  })

  it('REGRESSION: records a skill at its skills-section line, not an earlier bullet', () => {
    // Found by running the deployed app. Taking the first mention recorded Java
    // at the bullet that happens to name it, so everything downstream believed
    // Java was not in the skills section — and the match report told the
    // candidate to move a skill they had already listed.
    const java = snapshot.skills.find((skill) => skill.normalizedName === 'java')
    const skillsSection = snapshot.sections.find((section) => section.type === 'SKILLS')!

    expect(java).toBeDefined()
    expect(java!.lineStart).toBeGreaterThanOrEqual(skillsSection.startLine)
    expect(java!.lineStart).toBeLessThanOrEqual(skillsSection.endLine)
  })

  it('finds the listed skills', () => {
    const names = snapshot.skills.map((skill) => skill.normalizedName)
    expect(names).toEqual(
      expect.arrayContaining(['java', 'spring boot', 'postgresql', 'react', 'git']),
    )
  })
})

describe('ATS rubric', () => {
  const assessment = evaluateAts(snapshotOf(RESUME))

  it('scores within bounds and reports both problems and passes', () => {
    expect(assessment.overallScore).toBeGreaterThanOrEqual(0)
    expect(assessment.overallScore).toBeLessThanOrEqual(100)
    expect(assessment.findings.some((f) => f.severity === 'PASS')).toBe(true)
    expect(assessment.findings.some((f) => f.severity !== 'PASS')).toBe(true)
  })

  it('quotes the resume for a finding about specific text', () => {
    // A score without a quote from the resume it came from is an assertion,
    // not analysis.
    const passive = assessment.findings.find((f) => f.code === 'PASSIVE_PHRASING')
    expect(passive).toBeDefined()
    expect(passive!.evidence).toContain('Responsible for testing')
    expect(passive!.recommendation).toBeTruthy()
  })

  it('REGRESSION: does not claim dated experience is undated', () => {
    expect(assessment.findings.find((f) => f.code === 'UNDATED_EXPERIENCE')).toBeUndefined()
    expect(assessment.categoryScores.STRUCTURE).toBe(100)
  })

  it('orders findings most urgent first', () => {
    const rank: Record<string, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1, PASS: 0 }
    for (let i = 1; i < assessment.findings.length; i++) {
      expect(rank[assessment.findings[i - 1].severity]).toBeGreaterThanOrEqual(
        rank[assessment.findings[i].severity],
      )
    }
  })

  it('the five category weights sum to 100', async () => {
    const { ATS_CATEGORIES } = await import('../ats')
    const total = Object.values(ATS_CATEGORIES).reduce((sum, meta) => sum + meta.weight, 0)
    expect(total).toBe(100)
  })

  it('survives an empty resume without throwing', () => {
    const empty: ResumeSnapshot = {
      lines: [], sections: [], contact: EMPTY_CONTACT, skills: [], education: [],
      experience: [], warningCodes: new Set(), pageCount: null, wordCount: null, charCount: null,
    }
    const result = evaluateAts(empty)
    expect(result.overallScore).toBeGreaterThanOrEqual(0)
    expect(result.findings.length).toBeGreaterThan(0)
  })
})

describe('job matching', () => {
  const posting = parseJobPosting(POSTING)
  const outcome = matchResume(snapshotOf(RESUME), posting)

  // MatchOutcome carries one list with a verdict per skill; the API layer is
  // what splits it for the client.
  const withVerdict = (verdict: string) =>
    outcome.skills.filter((skill) => skill.verdict === verdict)

  it('splits must-haves from nice-to-haves', () => {
    const required = posting.skills.filter((s) => s.required).map((s) => s.normalizedName)
    const optional = posting.skills.filter((s) => !s.required).map((s) => s.normalizedName)
    expect(required).toEqual(expect.arrayContaining(['java', 'docker', 'kubernetes']))
    expect(optional).toEqual(expect.arrayContaining(['redis', 'kafka']))
  })

  it('REGRESSION: does not report SQL as missing when the resume lists PostgreSQL', () => {
    // Found by running the deployed app. A false gap is worse than a missing
    // one: it costs the user's trust in every other gap on the list.
    expect(withVerdict('MISSING').map((s) => s.normalizedName)).not.toContain('sql')
    expect(withVerdict('MATCHED').map((s) => s.normalizedName)).toContain('sql')
  })

  it('REGRESSION: does not suggest moving a skill already in the skills section', () => {
    const surface = outcome.suggestions.filter((s) => s.kind === 'SURFACE_SKILL')
    expect(surface.map((s) => s.title).join(' ')).not.toMatch(/Java|Spring|PostgreSQL/)
  })

  it('REGRESSION: names skills the way a person writes them', () => {
    // "Gap: kubernetes" reads as sloppy in a report whose whole claim is
    // precision, and the same string is shown inside interview questions.
    const names = withVerdict('MISSING').map((s) => s.displayName)
    expect(names).toContain('Docker')
    expect(names).toContain('Kubernetes')
    expect(names.every((name) => name === name.trim() && name[0] === name[0].toUpperCase())).toBe(true)
  })

  it('quotes the posting line behind every gap', () => {
    const docker = withVerdict('MISSING').find((s) => s.normalizedName === 'docker')
    expect(docker?.jdEvidence?.toLowerCase()).toContain('docker')
  })

  it('never invents experience in a suggestion', () => {
    // The system improves how the truth is expressed. Where a figure is needed
    // the candidate is asked for it rather than given one.
    for (const suggestion of outcome.suggestions) {
      if (suggestion.before && suggestion.kind === 'QUANTIFY') {
        expect(suggestion.after).toContain(suggestion.before)
        expect(suggestion.after).toContain('[')
      }
    }
  })

  it('does not blame the candidate for a posting that states no requirements', () => {
    const vague = matchResume(snapshotOf(RESUME), parseJobPosting('We are hiring. Come join us.'))
    expect(vague.requiredSkillScore).toBe(100)
  })

  it('an empty posting does not throw', () => {
    expect(parseJobPosting('').skills).toHaveLength(0)
    expect(parseJobPosting(null).skills).toHaveLength(0)
  })
})

describe('implied skills', () => {
  it('maps a database to SQL but not the reverse', () => {
    expect(impliedBy('sql', ['postgresql'])).toBe('postgresql')
    expect(impliedBy('postgresql', ['sql'])).toBeNull()
  })

  it('labels irregular names correctly', () => {
    expect(skillLabel('sql')).toBe('SQL')
    expect(skillLabel('node.js')).toBe('Node.js')
    expect(skillLabel('docker')).toBe('Docker')
    expect(skillLabel('spring boot')).toBe('Spring Boot')
  })
})

describe('interview', () => {
  const snapshot = snapshotOf(RESUME)

  it('builds questions from the resume and the stated gaps', () => {
    const questions = generateQuestions(snapshot, ['Docker'], 'JOB_SPECIFIC', 4, hashSeed('t'))
    expect(questions).toHaveLength(4)
    expect(JSON.stringify(questions)).toContain('Docker')
    questions.forEach((question) => {
      expect(question.rationale).toBeTruthy()
      expect(question.expectedPoints.length).toBeGreaterThan(0)
    })
  })

  it('is reproducible for the same inputs', () => {
    const seed = hashSeed('user', 'resume', 'GENERAL', 5)
    const a = generateQuestions(snapshot, [], 'GENERAL', 5, seed)
    const b = generateQuestions(snapshot, [], 'GENERAL', 5, seed)
    expect(a).toEqual(b)
  })

  it('still produces a full session with no resume at all', () => {
    expect(generateQuestions(null, [], 'BEHAVIOURAL', 5, 1)).toHaveLength(5)
  })

  it('refuses to judge an answer too short to judge', () => {
    const assessment = evaluateAnswer('I did it well.', 'BEHAVIOURAL', [], null)
    expect(assessment.overallScore).toBeLessThan(30)
    expect(assessment.improvements.length).toBeGreaterThan(0)
  })

  it('rewards a structured, specific answer', () => {
    const strong = evaluateAnswer(
      `Last summer I was working on the reporting service at Acme, and the monthly
       report took 4 minutes because we issued one query per team. I profiled it,
       found 30 separate PostgreSQL round trips, and I decided to batch them into a
       single join. As a result generation came down to 25 seconds and the 3 teams
       stopped filing tickets. Looking back I would have measured before guessing.`,
      'BEHAVIOURAL',
      ['Context: what the situation actually was', 'The outcome, with a number if one exists'],
      null,
    )
    expect(strong.structureScore).toBeGreaterThanOrEqual(80)
    expect(strong.specificityScore).toBeGreaterThanOrEqual(70)
    expect(strong.relevanceScore).toBeGreaterThan(0)
    expect(strong.strengths.length).toBeGreaterThan(0)
  })

  it('scores the same answer identically every time', () => {
    const answer = 'When I was at Acme I built a service in Java that cut latency by 40%. As a result the team shipped weekly rather than monthly, and I learned to measure first.'
    expect(evaluateAnswer(answer, 'TECHNICAL', [], 'Java')).toEqual(
      evaluateAnswer(answer, 'TECHNICAL', [], 'Java'),
    )
  })

  it('scores an empty answer zero without throwing', () => {
    expect(evaluateAnswer('', 'TECHNICAL', [], null).overallScore).toBe(0)
    expect(evaluateAnswer(null, 'TECHNICAL', [], null).overallScore).toBe(0)
  })
})
