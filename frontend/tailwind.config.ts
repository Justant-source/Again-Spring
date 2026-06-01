import type { Config } from 'tailwindcss';

const config: Config = {
  darkMode: ['class'],
  content: [
    './app/**/*.{ts,tsx}',
    './components/**/*.{ts,tsx}',
    './lib/**/*.{ts,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          'Pretendard Variable',
          'Pretendard',
          '-apple-system',
          'system-ui',
          'Malgun Gothic',
          'sans-serif',
        ],
        serif: ['Noto Serif KR', 'Nanum Myeongjo', 'serif'],
      },
      colors: {
        // Tone L — Letter (편지지)
        'tone-l': {
          bg: '#F5EFE6',
          card: '#FBF6EC',
          ink: '#2B2B2B',
          sub: '#6E6151', // 가독성 개선: 구 #8A7F6B → AA WCAG 기준 L-bg 위 ≥4.5:1
          border: '#D9CFBD',
          point: '#8A3A1F',
        },
        // Tone P — Pastel (결과·공유)
        'tone-p': {
          bg: '#FBF3EC',
          card: '#FFF8F0',
          ink: '#5C4030',
          sub: '#876B54', // 가독성 개선: 구 #A08670 → AA WCAG 기준 P-bg 위 ≥4.5:1
          border: '#EADFD0',
          a: '#F4A896',
          b: '#A8C8B4',
        },
        // Tone Q — Quiet (PDF·Premium)
        'tone-q': {
          bg: '#FAFAF7',
          card: '#FFFFFF',
          ink: '#1A1A1A',
          sub: '#9B9890',
          border: '#E8E6E0',
          point: '#6B7A8F',
        },
        canvas: '#EFE9DE',
      },
      borderRadius: {
        'letter': '3px',
        'pastel': '14px',
        'card-p': '18px',
      },
      boxShadow: {
        'phone': '0 8px 40px rgba(60, 40, 20, 0.08)',
      },
      keyframes: {
        blink: { '50%': { opacity: '0' } },
        'fade-in-up': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'blink': 'blink 1s infinite',
        'fade-in-up': 'fade-in-up 0.6s ease-out',
      },
    },
  },
  plugins: [require('tailwindcss-animate')],
};

export default config;
