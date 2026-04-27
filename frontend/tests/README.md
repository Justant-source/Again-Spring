# Frontend Test Infrastructure

Testing infrastructure for the Again Spring frontend using Vitest (unit/component/integration) + Playwright (E2E).

## Directory Structure

```
tests/
├── unit/              # Pure function/utility tests
├── component/         # React component tests (RTL)
├── integration/       # API/store integration tests
├── e2e/              # End-to-end tests (Playwright)
├── fixtures/         # Test data factories
├── setup.ts          # Vitest setup (MSW, mocks, polyfills)
└── README.md         # This file
```

## Running Tests

```bash
# Run all tests once
npm test

# Watch mode (rerun on file changes)
npm run test:watch

# Generate coverage report
npm run test:coverage

# Web dashboard
npm run test:ui

# E2E tests
npm run test:e2e

# A11y tests only
npm run test:a11y
```

## Writing Tests

### Import Fixtures

```typescript
import {
  createUser,
  createSession,
  createMessage,
  createSessionWithMessages,
  createSessionMessages,
  createMediatorMessage,
} from '@/tests/fixtures'

// Default user
const user = createUser()

// Custom user
const user = createUser({ nickname: 'Alice', isGuest: true })

// Guest user
const guest = createGuestUser()

// Solo session
const session = createSession()

// Duo session
const duoSession = createDuoSession()

// Message from USER_A
const msg = createMessage({ sender: 'USER_A', content: '...' })

// Mediator message
const med = createMediatorMessage(undefined, 'MEDIATOR_TO_B')

// Multiple messages
const messages = createSessionMessages(5, 'USER_A')

// Session with messages
const { session, messages } = createSessionWithMessages(10)

// User with multiple sessions
const { user, sessions } = createUserWithSessions(3)
```

### Component Tests (RTL)

```typescript
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { MyComponent } from '@/components/MyComponent'
import { createUser } from '@/tests/fixtures'

describe('MyComponent', () => {
  it('displays user name', () => {
    const user = createUser({ nickname: 'Alice' })
    render(<MyComponent user={user} />)

    expect(screen.getByText('Alice')).toBeInTheDocument()
  })

  it('handles click', async () => {
    const user = userEvent.setup()
    render(<MyComponent />)

    await user.click(screen.getByRole('button'))
    expect(screen.getByText('Clicked')).toBeInTheDocument()
  })
})
```

### Integration Tests

```typescript
import { describe, it, expect } from 'vitest'
import { createSessionWithMessages } from '@/tests/fixtures'

describe('Message API integration', () => {
  it('creates a session with messages', () => {
    const { session, messages } = createSessionWithMessages(5)

    expect(messages).toHaveLength(5)
    expect(messages[0].sender).toBe('USER_A')
  })
})
```

### E2E Tests (Playwright)

```typescript
import { test, expect } from '@playwright/test'

test.describe('Chat flow', () => {
  test('user can send a message', async ({ page }) => {
    await page.goto('/chat')
    await page.fill('[data-testid=input]', 'Hello')
    await page.click('button:has-text("Send")')

    await expect(page.locator('text=Hello')).toBeVisible()
  })
})
```

### A11y Tests

```typescript
import { test, expect } from '@playwright/test'
import { injectAxe, checkA11y } from 'axe-playwright'

test('@a11y home page', async ({ page }) => {
  await page.goto('/')
  await injectAxe(page)
  await checkA11y(page)
})
```

## Setup & Mocks

`tests/setup.ts` provides:

- **MSW server** — mocked API responses (uses `mocks/handlers`)
- **RTL cleanup** — after each test
- **localStorage/sessionStorage** — automatically cleared
- **Next.js mocks**:
  - `useRouter()` returns mocked router
  - `useSearchParams()` returns empty URLSearchParams
  - `usePathname()` returns `/`
  - `redirect()`, `notFound()` are vi.fn()
  - `next/image` → plain `<img>`
  - `next/link` → plain `<a>`
- **DOM polyfills**:
  - `window.matchMedia()`
  - `IntersectionObserver`
  - `ResizeObserver`

All setup runs automatically before each test suite.

## Coverage Targets

From `vitest.config.ts`:

- **Lines**: 80% (library code)
- **Functions**: 80%
- **Branches**: 70%
- **Statements**: 80%

Excluded:
- `mocks/**`
- `**/*.d.ts`
- `app/**/page.tsx` (route files)
- `app/**/layout.tsx` (layout files)

## Tips

1. **Always use fixtures** — they ensure consistency and reduce boilerplate
2. **Use data-testid** — better than testing implementation details
3. **Test user interactions** — use `@testing-library/user-event` over fireEvent
4. **Keep MSW handlers synced** — with `shared/docs/api/rest-spec.md`
5. **Check HAX compliance** — when testing components, refer to `frontend/docs/ux/hax-checklist.md`
6. **Run coverage regularly** — `npm run test:coverage` catches regressions
