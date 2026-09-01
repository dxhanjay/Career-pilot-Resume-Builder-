import type { Config } from 'drizzle-kit'

export default {
  schema: './src/db/schema.ts',
  out: './drizzle',
  dialect: 'postgresql',
  dbCredentials: {
    url: process.env.DATABASE_URL!,
  },
  // Every change goes through a generated SQL file that is reviewed and
  // committed. `push` mutates the live schema from the current TypeScript with
  // no review step and no rollback, which is how two environments quietly
  // diverge.
  strict: true,
  verbose: true,
} satisfies Config
