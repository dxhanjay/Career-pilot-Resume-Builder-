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
async function main() {
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
