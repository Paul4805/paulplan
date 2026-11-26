/** @type {import('next').NextConfig} */
const nextConfig = {
  // Firebase Hosting on the Spark plan cannot run SSR, and this app has no
  // server work to do: every page is client-rendered and talks to Firestore
  // directly. Static export keeps hosting free and single-vendor.
  output: 'export',
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
}

export default nextConfig
