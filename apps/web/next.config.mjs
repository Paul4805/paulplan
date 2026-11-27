/** @type {import('next').NextConfig} */
const nextConfig = {
  // Firebase Hosting on the Spark plan cannot run SSR, and this app has no
  // server work to do: every page is client-rendered and talks to Firestore
  // directly. Static export keeps hosting free and single-vendor.
  output: 'export',
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
  // `next build` and `next dev` must not share .next: a build while dev is
  // running leaves dev serving 404s for its own chunks.
  distDir: process.env.NEXT_DIST_DIR || '.next',
}

export default nextConfig
