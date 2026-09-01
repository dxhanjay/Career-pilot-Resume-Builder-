import { indexOfToken } from './text'

/**
 * The skill lexicon.
 *
 * A curated list rather than an embedding model, for the same reason the rest of
 * the pipeline is deterministic: matching has to be explainable. "Your resume
 * says React and this posting asks for React" is a claim a student can check.
 * "Cosine similarity 0.83" is not.
 */

export type SkillCategory =
  | 'LANGUAGE'
  | 'FRAMEWORK'
  | 'DATABASE'
  | 'CLOUD_DEVOPS'
  | 'TOOL'
  | 'CONCEPT'
  | 'SOFT_SKILL'

export interface SkillEntry {
  readonly canonical: string
  readonly category: SkillCategory
  /**
   * Whether the name collides with ordinary English.
   *
   * "R", "Go", "C" and "Rust" are real technologies and real words. Matching
   * them anywhere in a resume produces nonsense — "go" appears in half of all
   * bullets — so they are only matched inside a skills block, where the
   * surrounding context makes the reading unambiguous.
   */
  readonly ambiguous: boolean
  readonly aliases: readonly string[]
}

export interface SkillHit {
  readonly entry: SkillEntry
  readonly start: number
}

const entry = (
  canonical: string,
  category: SkillCategory,
  aliases: string[] = [],
  ambiguous = false,
): SkillEntry => ({
  canonical,
  category,
  ambiguous,
  // The canonical name is always searchable, and duplicates are harmless.
  aliases: [canonical, ...aliases].map((alias) => alias.toLowerCase()),
})

export const SKILL_LEXICON: readonly SkillEntry[] = [
  // --- Languages ---------------------------------------------------------
  entry('java', 'LANGUAGE', ['java 8', 'java 11', 'java 17', 'java 21', 'core java']),
  entry('python', 'LANGUAGE', ['python3', 'py']),
  entry('javascript', 'LANGUAGE', ['js', 'es6', 'ecmascript', 'vanilla js']),
  entry('typescript', 'LANGUAGE', ['ts']),
  entry('c++', 'LANGUAGE', ['cpp', 'c plus plus']),
  entry('c#', 'LANGUAGE', ['csharp', 'c sharp']),
  entry('php', 'LANGUAGE'),
  entry('ruby', 'LANGUAGE'),
  entry('swift', 'LANGUAGE'),
  entry('kotlin', 'LANGUAGE'),
  entry('scala', 'LANGUAGE'),
  entry('dart', 'LANGUAGE'),
  entry('perl', 'LANGUAGE'),
  entry('haskell', 'LANGUAGE'),
  entry('elixir', 'LANGUAGE'),
  entry('lua', 'LANGUAGE'),
  entry('matlab', 'LANGUAGE'),
  entry('sql', 'LANGUAGE', ['pl/sql', 't-sql']),
  entry('html', 'LANGUAGE', ['html5']),
  entry('css', 'LANGUAGE', ['css3']),
  entry('bash', 'LANGUAGE', ['shell scripting', 'shell script']),
  entry('go', 'LANGUAGE', ['golang'], true),
  entry('rust', 'LANGUAGE', [], true),
  entry('r', 'LANGUAGE', [], true),
  entry('c', 'LANGUAGE', [], true),

  // --- Frameworks and libraries -----------------------------------------
  entry('react', 'FRAMEWORK', ['reactjs', 'react.js']),
  entry('next.js', 'FRAMEWORK', ['nextjs', 'next js']),
  entry('angular', 'FRAMEWORK', ['angularjs']),
  entry('vue', 'FRAMEWORK', ['vuejs', 'vue.js']),
  entry('svelte', 'FRAMEWORK', ['sveltekit']),
  entry('node.js', 'FRAMEWORK', ['nodejs', 'node js', 'node']),
  entry('express', 'FRAMEWORK', ['expressjs', 'express.js']),
  entry('spring boot', 'FRAMEWORK', ['springboot']),
  entry('spring', 'FRAMEWORK', ['spring framework', 'spring mvc']),
  entry('hibernate', 'FRAMEWORK', ['jpa']),
  entry('django', 'FRAMEWORK'),
  entry('flask', 'FRAMEWORK'),
  entry('fastapi', 'FRAMEWORK'),
  entry('rails', 'FRAMEWORK', ['ruby on rails']),
  entry('laravel', 'FRAMEWORK'),
  entry('.net', 'FRAMEWORK', ['dotnet', 'asp.net', '.net core']),
  entry('flutter', 'FRAMEWORK'),
  entry('react native', 'FRAMEWORK'),
  entry('tailwind', 'FRAMEWORK', ['tailwindcss', 'tailwind css']),
  entry('bootstrap', 'FRAMEWORK'),
  entry('jquery', 'FRAMEWORK'),
  entry('tensorflow', 'FRAMEWORK'),
  entry('pytorch', 'FRAMEWORK'),
  entry('scikit-learn', 'FRAMEWORK', ['sklearn', 'scikit learn']),
  entry('pandas', 'FRAMEWORK'),
  entry('numpy', 'FRAMEWORK'),
  entry('opencv', 'FRAMEWORK'),

  // --- Databases ---------------------------------------------------------
  entry('postgresql', 'DATABASE', ['postgres', 'psql']),
  entry('mysql', 'DATABASE'),
  entry('mongodb', 'DATABASE', ['mongo']),
  entry('redis', 'DATABASE'),
  entry('sqlite', 'DATABASE'),
  entry('oracle', 'DATABASE', ['oracle db']),
  entry('sql server', 'DATABASE', ['mssql', 'microsoft sql server']),
  entry('dynamodb', 'DATABASE'),
  entry('cassandra', 'DATABASE'),
  entry('elasticsearch', 'DATABASE', ['elastic search']),
  entry('firebase', 'DATABASE', ['firestore']),
  entry('supabase', 'DATABASE'),
  entry('neo4j', 'DATABASE'),

  // --- Cloud and DevOps --------------------------------------------------
  entry('aws', 'CLOUD_DEVOPS', ['amazon web services']),
  entry('azure', 'CLOUD_DEVOPS', ['microsoft azure']),
  entry('gcp', 'CLOUD_DEVOPS', ['google cloud', 'google cloud platform']),
  entry('docker', 'CLOUD_DEVOPS', ['containerisation', 'containerization']),
  entry('kubernetes', 'CLOUD_DEVOPS', ['k8s']),
  entry('terraform', 'CLOUD_DEVOPS'),
  entry('ansible', 'CLOUD_DEVOPS'),
  entry('jenkins', 'CLOUD_DEVOPS'),
  entry('github actions', 'CLOUD_DEVOPS'),
  entry('gitlab ci', 'CLOUD_DEVOPS'),
  entry('ci/cd', 'CLOUD_DEVOPS', ['cicd', 'continuous integration']),
  entry('nginx', 'CLOUD_DEVOPS'),
  entry('kafka', 'CLOUD_DEVOPS', ['apache kafka']),
  entry('rabbitmq', 'CLOUD_DEVOPS'),
  entry('vercel', 'CLOUD_DEVOPS'),
  entry('heroku', 'CLOUD_DEVOPS'),
  entry('lambda', 'CLOUD_DEVOPS', ['aws lambda']),
  entry('s3', 'CLOUD_DEVOPS', ['amazon s3']),

  // --- Tools -------------------------------------------------------------
  entry('git', 'TOOL', ['github', 'gitlab', 'version control']),
  entry('jira', 'TOOL'),
  entry('figma', 'TOOL'),
  entry('postman', 'TOOL'),
  entry('linux', 'TOOL', ['unix', 'ubuntu']),
  entry('junit', 'TOOL'),
  entry('jest', 'TOOL'),
  entry('cypress', 'TOOL'),
  entry('selenium', 'TOOL'),
  entry('playwright', 'TOOL'),
  entry('webpack', 'TOOL'),
  entry('vite', 'TOOL'),
  entry('maven', 'TOOL'),
  entry('gradle', 'TOOL'),
  entry('excel', 'TOOL', ['microsoft excel']),
  entry('tableau', 'TOOL'),
  entry('power bi', 'TOOL', ['powerbi']),

  // --- Concepts ----------------------------------------------------------
  entry('rest api', 'CONCEPT', ['rest', 'restful', 'rest apis']),
  entry('graphql', 'CONCEPT'),
  entry('microservices', 'CONCEPT'),
  entry('agile', 'CONCEPT', ['scrum', 'kanban']),
  entry('tdd', 'CONCEPT', ['test driven development']),
  entry('oop', 'CONCEPT', ['object oriented programming', 'object-oriented']),
  entry('data structures', 'CONCEPT', ['dsa']),
  entry('algorithms', 'CONCEPT'),
  entry('machine learning', 'CONCEPT', ['ml']),
  entry('deep learning', 'CONCEPT'),
  entry('nlp', 'CONCEPT', ['natural language processing']),
  entry('computer vision', 'CONCEPT'),
  entry('data analysis', 'CONCEPT', ['data analytics']),
  entry('system design', 'CONCEPT'),
  entry('web sockets', 'CONCEPT', ['websocket', 'websockets']),
  entry('authentication', 'CONCEPT', ['oauth', 'jwt']),

  // --- Soft skills -------------------------------------------------------
  entry('leadership', 'SOFT_SKILL', ['team lead']),
  entry('communication', 'SOFT_SKILL'),
  entry('teamwork', 'SOFT_SKILL', ['collaboration']),
  entry('problem solving', 'SOFT_SKILL', ['problem-solving']),
  entry('mentoring', 'SOFT_SKILL', ['mentorship']),
  entry('public speaking', 'SOFT_SKILL'),
]

/** Canonical name → entry, for callers that already know what they want. */
export const SKILLS_BY_CANONICAL: ReadonlyMap<string, SkillEntry> = new Map(
  SKILL_LEXICON.map((skill) => [skill.canonical, skill]),
)

/**
 * Finds every skill mentioned in a line.
 *
 * Matching is whole-token; see indexOfToken for why that matters.
 *
 * @param includeAmbiguous whether ordinary-English skill names may match. True
 *   only inside a skills section, or when reading a job posting, where the text
 *   is dense with technology names and the surrounding words disambiguate.
 */
export function findSkillsIn(line: string, includeAmbiguous: boolean): SkillHit[] {
  if (!line || !line.trim()) return []

  const haystack = line.toLowerCase()
  const hits: SkillHit[] = []

  for (const skill of SKILL_LEXICON) {
    if (skill.ambiguous && !includeAmbiguous) continue

    for (const alias of skill.aliases) {
      const at = indexOfToken(haystack, alias)
      if (at >= 0) {
        hits.push({ entry: skill, start: at })
        break
      }
    }
  }

  return hits.sort((a, b) => a.start - b.start)
}
