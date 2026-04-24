/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  eslint: {
    ignoreDuringBuilds: false,
    dirs: ['app', 'components', 'lib', 'mocks'],
  },
};

export default nextConfig;
