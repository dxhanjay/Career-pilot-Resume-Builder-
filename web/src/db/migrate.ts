import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { neon } from '@neondatabase/serverless'
import { drizzle } from 'drizzle-orm/neon-http'
import { migrate } from 'drizzle-orm/neon-http/migrator'

/**
 * Applies pending migrations.
 *
 * Run once after provisioning the database, and again after any schema change:
 *
 *     npm run db:migrate
 *
 * Deliberately not run automatically on boot. On a serverless platform every
 * cold start would race every other cold start to take the migration lock, and
 * the first request after a deploy would carry the latency of a schema change.
 * A schema change is a deliberate act with a person watching it.
 */
/**
 * Loads .env.local if the variable is not already in the environment.
 *
 * Node does not read .env files on its own, and `vercel env pull` writes one —
 * so without this the script fails with "DATABASE_URL is not set" immediately
 * after the user has just successfully fetched it, which is a confusing place
 * to be. Anything already exported wins, so CI and production are unaffected.
 */
function loadLocalEnv(): void {
  if (process.env.DATABASE_URL) return

  const path = resolve(process.cwd(), '.env.local')
  if (!existsSync(path)) return

  for (const line of readFileSync(path, 'utf8').split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue

    const separator = trimmed.indexOf('=')
    if (separator < 0) continue

    const key = trimmed.slice(0, separator).trim()
    let value = trimmed.slice(separator + 1).trim()

    // Vercel quotes values that contain characters a shell would interpret,
    // and the quotes are not part of the connection string.
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1)
    }

    if (!process.env[key]) process.env[key] = value
  }
}

async function main() {
  loadLocalEnv()
  const url = process.env.DATABASE_URL
  if (!url) {
    console.error(
      'DATABASE_URL is not set.\n\n' +
        'Locally: put it in web/.env.local\n' +
        'From Vercel: npx vercel env pull .env.local',
    )
    process.exit(1)
  }

  const db = drizzle(neon(url))

  console.log('Applying migrations…')
  await migrate(db, { migrationsFolder: './drizzle' })
  console.log('Schema is up to date.')
}

main().catch((error) => {
  console.error('Migration failed:', error)
  process.exit(1)
})
