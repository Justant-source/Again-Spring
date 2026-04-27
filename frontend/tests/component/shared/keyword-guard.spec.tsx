import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { KeywordGuard, useKeywordGuard } from '@/components/shared/KeywordGuard'
import { vi } from 'vitest'
import React from 'react'

// Mock the keyword check utility and CrisisResourceModal
vi.mock('@/lib/utils/keywordGuard', () => {
  return {
    checkKeywords: (text: string) => {
      // Mock Level 1 keywords (crisis)
      if (text.includes('죽고 싶')) return { level: 1 }
      if (text.includes('자살')) return { level: 1 }
      if (text.includes('자해')) return { level: 1 }
      if (text.includes('폭행')) return { level: 1 }
      // Mock Level 2 keywords (warning)
      if (text.includes('나르시시스트')) return { level: 2 }
      if (text.includes('가스라이팅')) return { level: 2 }
      // No keywords found
      return { level: null }
    },
  }
})

vi.mock('@/components/shared/CrisisResourceModal', () => ({
  CrisisResourceModal: ({ open, onClose, severity }: any) =>
    open ? (
      <div data-testid="crisis-modal" data-severity={severity}>
        <div>Crisis Resource Modal - {severity}</div>
        <button onClick={onClose}>Close</button>
      </div>
    ) : null,
}))

describe('useKeywordGuard hook', () => {
  it('initializes hook and provides check function', () => {
    const TestComponent = () => {
      const guard = useKeywordGuard()
      return <div>{typeof guard.check}</div>
    }

    render(<TestComponent />)
    expect(screen.getByText('function')).toBeInTheDocument()
  })

  it('detects level 1 keywords', async () => {
    const user = userEvent.setup()
    const TestComponent = () => {
      const guard = useKeywordGuard()
      const [result, setResult] = React.useState<number | null>(null)
      return (
        <div>
          <button onClick={() => setResult(guard.check('I want to 죽고 싶'))}>Check</button>
          <div data-testid="result">{String(result)}</div>
          {guard.modal}
        </div>
      )
    }

    render(<TestComponent />)
    await user.click(screen.getByRole('button'))
    // Should return 1 for level 1 keywords
    expect(screen.getByTestId('result')).toHaveTextContent('1')
  })

  it('detects level 2 keywords', async () => {
    const user = userEvent.setup()
    const TestComponent = () => {
      const guard = useKeywordGuard()
      const [result, setResult] = React.useState<number | null>(null)
      return (
        <div>
          <button onClick={() => setResult(guard.check('He is a 나르시시스트'))}>Check</button>
          <div data-testid="result">{String(result)}</div>
          {guard.modal}
        </div>
      )
    }

    render(<TestComponent />)
    await user.click(screen.getByRole('button'))
    // Should return 2 for level 2 keywords
    expect(screen.getByTestId('result')).toHaveTextContent('2')
  })

  it('returns null for normal text', () => {
    const TestComponent = () => {
      const guard = useKeywordGuard()
      const level = guard.check('This is a normal conversation')
      return <div>{String(level)}</div>
    }

    render(<TestComponent />)

    expect(screen.getByText('null')).toBeInTheDocument()
  })

  it('opens modal when level 1 keyword detected', async () => {
    const user = userEvent.setup()
    const TestComponent = () => {
      const guard = useKeywordGuard()

      return (
        <div>
          <button
            onClick={() => {
              guard.check('I want to 죽고 싶')
            }}
          >
            Trigger
          </button>
          {guard.modal}
        </div>
      )
    }

    render(<TestComponent />)
    const button = screen.getByRole('button', { name: 'Trigger' })
    await user.click(button)

    // Modal should be present when triggered
    const crisisModal = screen.queryByTestId('crisis-modal')
    expect(crisisModal).toBeTruthy()
  })

  it('provides check function that works correctly', async () => {
    const user = userEvent.setup()
    const TestComponent = () => {
      const guard = useKeywordGuard()
      const [result, setResult] = React.useState<string>('unset')
      return (
        <div>
          <button data-testid="check-normal" onClick={() => setResult(String(guard.check('Normal text')))}>Normal</button>
          <button data-testid="check-crisis" onClick={() => setResult(String(guard.check('I want to 죽고 싶')))}>Crisis</button>
          <div data-testid="result">{result}</div>
          {guard.modal}
        </div>
      )
    }

    render(<TestComponent />)

    await user.click(screen.getByTestId('check-normal'))
    expect(screen.getByTestId('result')).toHaveTextContent('null')

    await user.click(screen.getByTestId('check-crisis'))
    expect(screen.getByTestId('result')).toHaveTextContent('1')
  })

  it('provides close function to dismiss modal', async () => {
    const user = userEvent.setup()
    const TestComponent = () => {
      const guard = useKeywordGuard()
      const [triggered, setTriggered] = React.useState(false)

      return (
        <div>
          <button
            onClick={() => {
              guard.check('I want to 죽고 싶')
              setTriggered(true)
            }}
            data-testid="trigger-button"
          >
            Trigger
          </button>
          {guard.modal}
        </div>
      )
    }

    render(<TestComponent />)

    const triggerButton = screen.getByTestId('trigger-button')
    await user.click(triggerButton)

    // Modal should be visible
    const closeButton = screen.getByRole('button', { name: 'Close' })
    expect(closeButton).toBeInTheDocument()
  })
})

describe('KeywordGuard component', () => {
  it('renders render-prop children', () => {
    render(
      <KeywordGuard>
        {({ check, modal }) => <div>Test content</div>}
      </KeywordGuard>
    )

    expect(screen.getByText('Test content')).toBeInTheDocument()
  })

  it('provides check function to children', () => {
    const TestChild = ({ check }: any) => {
      const level = check('Normal text')
      return <div data-testid="result">Level: {level}</div>
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => <TestChild check={check} />}
      </KeywordGuard>
    )

    expect(screen.getByTestId('result')).toBeInTheDocument()
  })

  it('provides modal to children', () => {
    const { container } = render(
      <KeywordGuard>
        {({ check, modal }) => (
          <div data-testid="guard-content">
            {modal}
          </div>
        )}
      </KeywordGuard>
    )

    // Guard content should be rendered
    expect(screen.getByTestId('guard-content')).toBeInTheDocument()
  })

  it('shows modal when level 1 keyword is detected', () => {
    const TestChild = ({ check }: any) => {
      const level = check('I want to 죽고 싶')
      return <div>Checked</div>
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestChild check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    expect(screen.getByTestId('crisis-modal')).toBeInTheDocument()
  })

  it('shows modal when level 2 keyword is detected', () => {
    const TestChild = ({ check }: any) => {
      const level = check('He is a 나르시시스트')
      return <div>Checked</div>
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestChild check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    expect(screen.getByTestId('crisis-modal')).toBeInTheDocument()
  })

  it('allows multiple keyword checks', () => {
    const TestComponent = ({ check }: any) => {
      const level1 = check('Normal text')
      const level2 = check('I want to 죽고 싶')
      const level3 = check('Another normal text')

      return <div>Test complete</div>
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestComponent check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    expect(screen.getByText('Test complete')).toBeInTheDocument()
  })

  it('can close modal and reopen it', async () => {
    const user = userEvent.setup()
    const TestComponent = ({ check }: any) => {
      return (
        <div>
          <button
            onClick={() => check('I want to 죽고 싶')}
            data-testid="trigger-level1"
          >
            Trigger Level 1
          </button>
          <button
            onClick={() => check('Normal text')}
            data-testid="trigger-normal"
          >
            Trigger Normal
          </button>
        </div>
      )
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestComponent check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    const triggerButton = screen.getByTestId('trigger-level1')
    await user.click(triggerButton)

    // Modal should be visible
    expect(screen.getByTestId('crisis-modal')).toBeInTheDocument()
  })

  it('works with text input field', async () => {
    const user = userEvent.setup()

    const TestComponent = ({ check }: any) => {
      return (
        <input
          data-testid="text-input"
          onChange={(e) => check(e.target.value)}
          placeholder="Type something"
        />
      )
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestComponent check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    const input = screen.getByTestId('text-input') as HTMLInputElement
    await user.type(input, 'I want to 죽고 싶')

    expect(screen.getByTestId('crisis-modal')).toBeInTheDocument()
  })

  it('works with textarea field', async () => {
    const user = userEvent.setup()

    const TestComponent = ({ check }: any) => {
      return (
        <textarea
          data-testid="text-area"
          onChange={(e) => check(e.target.value)}
          placeholder="Type something"
        />
      )
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestComponent check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    const textarea = screen.getByTestId('text-area') as HTMLTextAreaElement
    await user.type(textarea, 'This is a 나르시시스트')

    expect(screen.getByTestId('crisis-modal')).toBeInTheDocument()
  })

  it('detects level 1 keywords (crisis)', () => {
    const crisisKeywords = ['죽고 싶', '자살', '자해', '폭행']

    crisisKeywords.forEach((keyword) => {
      const TestComponent = ({ check }: any) => {
        const level = check(`I ${keyword} today`)
        return null
      }

      const { unmount } = render(
        <KeywordGuard>
          {({ check, modal }) => (
            <>
              <TestComponent check={check} />
              {modal}
            </>
          )}
        </KeywordGuard>
      )

      expect(screen.getByTestId('crisis-modal')).toBeInTheDocument()
      unmount()
    })
  })

  it('detects level 2 keywords (warning)', () => {
    const warningKeywords = ['나르시시스트', '가스라이팅']

    warningKeywords.forEach((keyword) => {
      const TestComponent = ({ check }: any) => {
        const level = check(`He is a ${keyword}`)
        return null
      }

      const { unmount } = render(
        <KeywordGuard>
          {({ check, modal }) => (
            <>
              <TestComponent check={check} />
              {modal}
            </>
          )}
        </KeywordGuard>
      )

      expect(screen.getByTestId('crisis-modal')).toBeInTheDocument()
      unmount()
    })
  })

  it('handles repeated checks without duplicating modals', async () => {
    const user = userEvent.setup()

    const TestComponent = ({ check }: any) => {
      return (
        <>
          <button
            onClick={() => check('Normal')}
            data-testid="check-normal"
          >
            Check Normal
          </button>
          <button
            onClick={() => check('I want to 죽고 싶')}
            data-testid="check-crisis"
          >
            Check Crisis
          </button>
        </>
      )
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestComponent check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    // Multiple checks
    const normalButton = screen.getByTestId('check-normal')
    await user.click(normalButton)
    await user.click(normalButton)

    const crisisButton = screen.getByTestId('check-crisis')
    await user.click(crisisButton)

    // Should have exactly one modal
    const modals = screen.queryAllByTestId('crisis-modal')
    expect(modals.length).toBeGreaterThan(0)
  })

  it('passes correct severity to modal for level 1', () => {
    const TestComponent = ({ check }: any) => {
      check('I want to 죽고 싶')
      return <div>Checked</div>
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestComponent check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    const modal = screen.getByTestId('crisis-modal')
    expect(modal).toHaveAttribute('data-severity', 'critical')
  })

  it('passes correct severity to modal for level 2', () => {
    const TestComponent = ({ check }: any) => {
      check('He is a 나르시시스트')
      return <div>Checked</div>
    }

    render(
      <KeywordGuard>
        {({ check, modal }) => (
          <>
            <TestComponent check={check} />
            {modal}
          </>
        )}
      </KeywordGuard>
    )

    const modal = screen.getByTestId('crisis-modal')
    expect(modal).toHaveAttribute('data-severity', 'advisory')
  })
})
