import { NextResponse } from 'next/server'
import { desc, or, sql } from 'drizzle-orm'
import { db } from '@/db'
import { users } from '@/db/schema'
import { handler, ok, requireAdmin } from '@/lib/api'

export const runtime = 'nodejs'

/** No password hash, no tokens. An admin console is a high-value target. */
export const GET = handler(async (request: Request): Promise<NextResponse> => {
  await requireAdmin()

  const query = new URL(request.url).searchParams.get('query')?.trim() ?? ''

  const rows = await db
    .select({
      id: users.id,
      email: users.email,
      fullName: users.fullName,
      role: users.role,
      status: users.status,
      lastLoginAt: users.lastLoginAt,
      createdAt: users.createdAt,
    })
    .from(users)
    .where(
      query
        ? or(
            sql`lower(${users.email}) like ${'%' + query.toLowerCase() + '%'}`,
            sql`lower(${users.fullName}) like ${'%' + query.toLowerCase() + '%'}`,
          )
        : undefined,
    )
    .orderBy(desc(users.createdAt))
    .limit(100)

  return ok(rows)
})
