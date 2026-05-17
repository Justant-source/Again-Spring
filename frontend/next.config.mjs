/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  eslint: {
    ignoreDuringBuilds: false,
    dirs: ['app', 'components', 'lib', 'mocks'],
  },

  // 로컬 dev(nginx 없는 환경)에서 /api/* 요청을 BE로 프록시.
  // MSW on 상태에서는 브라우저 fetch가 service worker에서 가로채지므로 무해.
  // Docker/nginx 환경에서는 nginx가 /api/* 를 먼저 라우팅하므로 이 설정에 도달하지 않음.
  async rewrites() {
    const apiBase = process.env.API_BASE_URL || 'http://localhost:8080';
    return [
      {
        source: '/api/:path*',
        destination: `${apiBase}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
