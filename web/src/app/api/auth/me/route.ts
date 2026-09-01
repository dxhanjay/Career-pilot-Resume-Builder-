import { NextResponse } from 'next/server'
import { handler, ok, requireUser } from '@/lib/api'

export const runtime = 'nodejs'

export const GET = handler(async (): Promise<NextResponse> => ok(await requireUser()))
