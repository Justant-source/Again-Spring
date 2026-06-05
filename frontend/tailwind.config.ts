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
          'Noto Sans KR',
          '-apple-system',
          'system-ui',
          'Malgun Gothic',
          'sans-serif',
        ],
        serif: ['Noto Sans KR', '-apple-system', 'system-ui', 'Malgun Gothic', 'sans-serif'],
      },
      colors: {
        // shadcn/ui CSS 변수 → Tailwind 유틸리티 매핑
        // bg-background, bg-popover, bg-card 등이 동작하지 않으면 Dialog·Dropdown이 투명해짐
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        // Tone L — Letter (편지지 · 미스트 세이지)
        'tone-l': {
          bg: '#EDF1E8',
          card: '#F7F9F2',
          ink: '#2E3A2E',
          sub: '#7C8A77',
          border: '#D3DCC9',
          point: '#8A3A1F',
        },
        // Tone P — Pastel (결과·공유 · 미스트 세이지)
        'tone-p': {
          bg: '#EFF4EA',
          card: '#F7FAF2',
          ink: '#2E3A2E',
          sub: '#7C8A77',
          border: '#D3DCC9',
          a: '#C9785A', // 작성자(피치)
          b: '#5F8F76', // 상대방(세이지)
        },
        // 진영색 — 작성자(피치) vs 상대방(세이지) · 중립 식별색
        author: { DEFAULT: '#C9785A', bg: '#F6E6DD', dk: '#A55C3E' },
        partner: { DEFAULT: '#5F8F76', bg: '#E6EFE8', dk: '#487961' },
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
        // shadcn/ui가 사용하는 반지름 (sm:rounded-lg, rounded-md 등)
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
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
