/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,

  // Resume parsing runs in the Node runtime, not the Edge runtime: unpdf and
  // mammoth both need Node APIs. Marking them external stops Next from trying
  // to bundle them, which is what produces the "Can't resolve 'fs'" build
  // failure that looks like a missing dependency and is not one.
  serverExternalPackages: ['unpdf', 'mammoth'],

  eslint: {
    // Lint is a separate step in CI. A lint warning should not be able to fail
    // a production deploy that would otherwise be correct.
    ignoreDuringBuilds: true,
  },
}

export default nextConfig
