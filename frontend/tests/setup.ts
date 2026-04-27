import '@testing-library/jest-dom'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { server } from '@/mocks/server'

// ============================================================================
// MSW Server Setup
// ============================================================================

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' })
})

afterEach(() => {
  server.resetHandlers()
})

afterAll(() => {
  server.close()
})

// ============================================================================
// RTL Cleanup
// ============================================================================

afterEach(() => {
  cleanup()
})

// ============================================================================
// LocalStorage Cleanup
// ============================================================================

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})

// ============================================================================
// Next.js Navigation Mocks
// ============================================================================

vi.mock('next/navigation', () => ({
  useRouter: vi.fn(() => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    refresh: vi.fn(),
  })),
  useSearchParams: vi.fn(() => new URLSearchParams()),
  usePathname: vi.fn(() => '/'),
  useParams: vi.fn(() => ({})),
  redirect: vi.fn(),
  notFound: vi.fn(),
}))

// ============================================================================
// Next.js Image Mock
// ============================================================================

vi.mock('next/image', () => ({
  default: (props: any) => {
    const { src, alt, ...rest } = props
    // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text
    return require('react').createElement('img', { ...rest, src, alt })
  },
}))

// ============================================================================
// Next.js Link Mock
// ============================================================================

vi.mock('next/link', () => ({
  default: (props: any) => {
    const { children, href, ...rest } = props
    return require('react').createElement('a', { href, ...rest }, children)
  },
}))

// ============================================================================
// window.matchMedia Mock
// ============================================================================

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// ============================================================================
// IntersectionObserver Mock
// ============================================================================

global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  takeRecords() {
    return []
  }
  unobserve() {}
} as any

// ============================================================================
// ResizeObserver Mock
// ============================================================================

global.ResizeObserver = class ResizeObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  unobserve() {}
} as any

// ============================================================================
// HTMLElement.scrollIntoView Mock
// ============================================================================

Element.prototype.scrollIntoView = vi.fn() as any
