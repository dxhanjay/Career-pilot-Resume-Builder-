import { neon } from '@neondatabase/serverless'
import { drizzle, type NeonHttpDatabase } from 'drizzle-orm/neon-http'
import * as schema from './schema'

/**
 * The database handle.
 *
 * Neon's HTTP driver rather than a TCP pool, because this runs in serverless
 * functions. A pool assumes a long-lived process that can keep connections open
 * between requests; a function created per request and frozen afterwards cannot,
 * and a pool there exhausts the database's connection limit under load while
 * most of those connections sit idle inside frozen instances.
 *
 * The trade is that each statement is its own HTTP round trip and interactive
 * transactions are unavailable. Nothing here needs one: the multi-statement
 * writes are all "insert a parent, then its children".
 *
 * <h2>Why this is lazy</h2>
 *
 * Connecting at module load would read DATABASE_URL during `next build`, where
 * it is legitimately absent — the build compiles routes without running them.
 * A missing variable would then fail the build with a database error, which
 * points at entirely the wrong problem. Resolving on first use means the build
 * succeeds and a genuinely unconfigured deployment fails at the first query,
 * with a message saying exactly what to do about it.
 */

let instance: NeonHttpDatabase<typeof schema> | null = null

function connect(): NeonHttpDatabase<typeof schema> {
  const url = process.env.DATABASE_URL
  if (!url) {
    throw new Error(
      'DATABASE_URL is not set. Add a Postgres database in the Vercel dashboard ' +
        '(Storage → Create → Neon); the integration sets this variable automatically. ' +
        'Then run `npm run db:migrate` to create the schema.',
    )
  }
  if (!instance) instance = drizzle(neon(url), { schema })
  return instance
}

/**
 * A proxy so callers keep writing `db.select()` while the real client is not
 * built until the first property access.
 */
export const db = new Proxy({} as NeonHttpDatabase<typeof schema>, {
  get(_target, property, receiver) {
    return Reflect.get(connect(), property, receiver)
  },
})

export { schema }
