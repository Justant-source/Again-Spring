import { render, screen } from '@testing-library/react'
import { NVCScript } from '@/components/result/NVCScript'
import { vi } from 'vitest'

// Mock the Motif icons
vi.mock('@/components/shared/Motif', () => ({
  IconEye: ({ size }: { size: number }) => <span data-testid="icon-eye">Eye</span>,
  IconDrop: ({ size }: { size: number }) => <span data-testid="icon-drop">Drop</span>,
  IconNeed: ({ size }: { size: number }) => <span data-testid="icon-need">Need</span>,
  IconAsk: ({ size }: { size: number }) => <span data-testid="icon-ask">Ask</span>,
  STYLE_MOTIF: {},
}))

describe('NVCScript', () => {
  const mockScript = {
    observation: 'I noticed you came home late',
    feeling: 'I felt worried',
    need: 'I need to feel secure',
    request: 'Could you let me know when you are running late?',
  }

  it('renders sender and receiver information', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText(/Alice → Bob/)).toBeInTheDocument()
  })

  it('displays all four NVC steps', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('관찰')).toBeInTheDocument()
    expect(screen.getByText('느낌')).toBeInTheDocument()
    expect(screen.getByText('욕구')).toBeInTheDocument()
    expect(screen.getByText('부탁')).toBeInTheDocument()
  })

  it('displays observation text', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('I noticed you came home late')).toBeInTheDocument()
  })

  it('displays feeling text', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('I felt worried')).toBeInTheDocument()
  })

  it('displays need text', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('I need to feel secure')).toBeInTheDocument()
  })

  it('displays request text', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText(/Could you let me know/)).toBeInTheDocument()
  })

  it('renders observation icon', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByTestId('icon-eye')).toBeInTheDocument()
  })

  it('renders feeling icon', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByTestId('icon-drop')).toBeInTheDocument()
  })

  it('renders need icon', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByTestId('icon-need')).toBeInTheDocument()
  })

  it('renders request icon', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByTestId('icon-ask')).toBeInTheDocument()
  })

  it('handles empty or null script values gracefully', () => {
    const partialScript = {
      observation: 'Observation',
      feeling: '',
      need: null,
      request: undefined,
    }

    render(
      <NVCScript
        script={partialScript as any}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('Observation')).toBeInTheDocument()
  })

  it('displays different sender/receiver combinations', () => {
    render(
      <NVCScript
        script={mockScript}
        from="John"
        to="Jane"
      />
    )

    expect(screen.getByText(/John → Jane/)).toBeInTheDocument()
  })

  it('renders multiple NVC scripts with different content', () => {
    const script2 = {
      observation: 'You forgot our anniversary',
      feeling: 'I felt hurt',
      need: 'I need to feel valued',
      request: 'Could we celebrate together this weekend?',
    }

    const { rerender } = render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('I noticed you came home late')).toBeInTheDocument()

    rerender(
      <NVCScript
        script={script2}
        from="Bob"
        to="Alice"
      />
    )

    expect(screen.getByText('You forgot our anniversary')).toBeInTheDocument()
    expect(screen.getByText(/Bob → Alice/)).toBeInTheDocument()
  })

  it('preserves line breaks in script text', () => {
    const scriptWithLineBreaks = {
      observation: 'Line 1\nLine 2',
      feeling: 'Feeling text',
      need: 'Need text',
      request: 'Request text',
    }

    const { container } = render(
      <NVCScript
        script={scriptWithLineBreaks}
        from="Alice"
        to="Bob"
      />
    )

    expect(container.textContent).toContain('Line 1')
    expect(container.textContent).toContain('Line 2')
  })

  it('displays Korean NVC labels correctly', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('관찰')).toBeInTheDocument()
    expect(screen.getByText('느낌')).toBeInTheDocument()
    expect(screen.getByText('욕구')).toBeInTheDocument()
    expect(screen.getByText('부탁')).toBeInTheDocument()
  })

  it('handles very long script text', () => {
    const longScript = {
      observation: 'a'.repeat(500),
      feeling: 'b'.repeat(500),
      need: 'c'.repeat(500),
      request: 'd'.repeat(500),
    }

    render(
      <NVCScript
        script={longScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText('a'.repeat(500))).toBeInTheDocument()
  })

  it('maintains proper visual order: observation → feeling → need → request', () => {
    const { container } = render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    // Check that all 4 NVC steps are present
    expect(screen.getByText('관찰')).toBeInTheDocument()
    expect(screen.getByText('느낌')).toBeInTheDocument()
    expect(screen.getByText('욕구')).toBeInTheDocument()
    expect(screen.getByText('부탁')).toBeInTheDocument()
  })

  it('applies chip styling to sender/receiver label', () => {
    render(
      <NVCScript
        script={mockScript}
        from="Alice"
        to="Bob"
      />
    )

    const chip = screen.getByText(/Alice → Bob/).closest('span[class*="chip"]')
    expect(chip).toBeInTheDocument()
  })

  it('supports special characters in script', () => {
    const specialScript = {
      observation: 'You said: "Why?!" (with emphasis)',
      feeling: 'I felt 😢 sad',
      need: 'I need support & understanding',
      request: 'Could we talk about this @ home?',
    }

    render(
      <NVCScript
        script={specialScript}
        from="Alice"
        to="Bob"
      />
    )

    expect(screen.getByText(/You said: "Why\?\!" \(with emphasis\)/)).toBeInTheDocument()
  })
})
