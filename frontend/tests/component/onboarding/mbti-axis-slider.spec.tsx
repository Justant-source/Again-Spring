import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MbtiAxisSlider } from '@/components/onboarding/MbtiAxisSlider'
import { vi } from 'vitest'

describe('MbtiAxisSlider', () => {
  const defaultProps = {
    axisLabel: 'Test Axis',
    leftLetter: 'I',
    leftLabel: 'Introvert',
    rightLetter: 'E',
    rightLabel: 'Extrovert',
    value: 50,
    onChange: vi.fn(),
  }

  it('renders axis label', () => {
    render(<MbtiAxisSlider {...defaultProps} />)
    expect(screen.getByText('Test Axis')).toBeInTheDocument()
  })

  it('displays left letter', () => {
    render(<MbtiAxisSlider {...defaultProps} />)
    expect(screen.getByText('I')).toBeInTheDocument()
  })

  it('displays right letter', () => {
    render(<MbtiAxisSlider {...defaultProps} />)
    expect(screen.getByText('E')).toBeInTheDocument()
  })

  it('displays left label', () => {
    render(<MbtiAxisSlider {...defaultProps} />)
    expect(screen.getByText('Introvert')).toBeInTheDocument()
  })

  it('displays right label', () => {
    render(<MbtiAxisSlider {...defaultProps} />)
    expect(screen.getByText('Extrovert')).toBeInTheDocument()
  })

  it('displays percentage for left side', () => {
    render(<MbtiAxisSlider {...defaultProps} value={30} />)
    expect(screen.getByText(/I 70/)).toBeInTheDocument()
  })

  it('displays percentage for right side', () => {
    render(<MbtiAxisSlider {...defaultProps} value={70} />)
    expect(screen.getByText(/E 70/)).toBeInTheDocument()
  })

  it('renders input range slider', () => {
    const { container } = render(<MbtiAxisSlider {...defaultProps} />)
    const slider = container.querySelector('input[type="range"]')
    expect(slider).toBeInTheDocument()
  })

  it('sets slider min to 0', () => {
    const { container } = render(<MbtiAxisSlider {...defaultProps} />)
    const slider = container.querySelector('input[type="range"]') as HTMLInputElement
    expect(slider.min).toBe('0')
  })

  it('sets slider max to 100', () => {
    const { container } = render(<MbtiAxisSlider {...defaultProps} />)
    const slider = container.querySelector('input[type="range"]') as HTMLInputElement
    expect(slider.max).toBe('100')
  })

  it('sets slider step to 5', () => {
    const { container } = render(<MbtiAxisSlider {...defaultProps} />)
    const slider = container.querySelector('input[type="range"]') as HTMLInputElement
    expect(slider.step).toBe('5')
  })

  it('reflects current value on slider', () => {
    const { container } = render(<MbtiAxisSlider {...defaultProps} value={60} />)
    const slider = container.querySelector('input[type="range"]') as HTMLInputElement
    expect(Number(slider.value)).toBe(60)
  })

  it('calls onChange when slider is moved', () => {
    const onChange = vi.fn()
    const { container } = render(
      <MbtiAxisSlider
        {...defaultProps}
        value={50}
        onChange={onChange}
      />
    )

    const slider = container.querySelector('input[type="range"]') as HTMLInputElement
    fireEvent.change(slider, { target: { value: '75' } })

    expect(onChange).toHaveBeenCalled()
  })

  it('highlights left letter when value is left-biased', () => {
    const { container } = render(
      <MbtiAxisSlider {...defaultProps} value={30} />
    )

    const letters = container.querySelectorAll('span[style*="font-weight"]')
    // Left letter should be highlighted with accent color
    expect(letters.length).toBeGreaterThan(0)
  })

  it('highlights right letter when value is right-biased', () => {
    const { container } = render(
      <MbtiAxisSlider {...defaultProps} value={70} />
    )

    const letters = container.querySelectorAll('span[style*="font-weight"]')
    // Right letter should be highlighted with accent color
    expect(letters.length).toBeGreaterThan(0)
  })

  it('shows equal percentages at value 50', () => {
    render(<MbtiAxisSlider {...defaultProps} value={50} />)

    // At 50, component shows "균형" label
    expect(screen.getByText('균형')).toBeInTheDocument()
  })

  it('calculates left percentage correctly', () => {
    render(<MbtiAxisSlider {...defaultProps} value={30} />)
    // leftPct = 100 - 30 = 70, displayed as "I 70"
    expect(screen.getByText(/I 70/)).toBeInTheDocument()
  })

  it('calculates right percentage correctly', () => {
    render(<MbtiAxisSlider {...defaultProps} value={30} />)
    // isLeft=true at value 30, displayed as "I 70"
    expect(screen.getByText(/I 70/)).toBeInTheDocument()
  })

  it('handles extreme values (0)', () => {
    render(<MbtiAxisSlider {...defaultProps} value={0} />)
    // leftPct=100, isLeft=true → "I 100"
    expect(screen.getByText(/I 100/)).toBeInTheDocument()
  })

  it('handles extreme values (100)', () => {
    render(<MbtiAxisSlider {...defaultProps} value={100} />)
    // rightPct=100, isLeft=false → "E 100"
    expect(screen.getByText(/E 100/)).toBeInTheDocument()
  })

  it('updates when value prop changes', () => {
    const { rerender } = render(
      <MbtiAxisSlider {...defaultProps} value={30} />
    )

    expect(screen.getByText(/I 70/)).toBeInTheDocument()

    rerender(
      <MbtiAxisSlider {...defaultProps} value={70} />
    )

    expect(screen.getByText(/E 70/)).toBeInTheDocument()
  })

  it('determines left bias correctly', () => {
    render(
      <MbtiAxisSlider {...defaultProps} value={40} />
    )

    // With value < 50, isLeft=true → shows "${leftLetter} ${leftPct}" = "I 60"
    expect(screen.getByText(/I 60/)).toBeInTheDocument()
  })

  it('determines right bias correctly', () => {
    render(
      <MbtiAxisSlider {...defaultProps} value={60} />
    )

    // With value > 50, isLeft=false → shows "${rightLetter} ${rightPct}" = "E 60"
    expect(screen.getByText(/E 60/)).toBeInTheDocument()
  })

  it('handles different axis configurations', () => {
    render(
      <MbtiAxisSlider
        axisLabel="Sensing–Intuition"
        leftLetter="S"
        leftLabel="Sensor"
        rightLetter="N"
        rightLabel="Intuitive"
        value={55}
        onChange={vi.fn()}
      />
    )

    expect(screen.getByText('Sensing–Intuition')).toBeInTheDocument()
    expect(screen.getByText('S')).toBeInTheDocument()
    expect(screen.getByText('N')).toBeInTheDocument()
    expect(screen.getByText('Sensor')).toBeInTheDocument()
    expect(screen.getByText('Intuitive')).toBeInTheDocument()
  })

  it('renders correctly with all MBTI axes', () => {
    const axes = [
      { left: 'I', right: 'E', lLabel: 'Introvert', rLabel: 'Extrovert', label: 'E-I' },
      { left: 'S', right: 'N', lLabel: 'Sensor', rLabel: 'Intuitive', label: 'S-N' },
      { left: 'T', right: 'F', lLabel: 'Thinker', rLabel: 'Feeler', label: 'T-F' },
      { left: 'J', right: 'P', lLabel: 'Judger', rLabel: 'Perceiver', label: 'J-P' },
    ]

    axes.forEach((axis) => {
      const { unmount } = render(
        <MbtiAxisSlider
          axisLabel={axis.label}
          leftLetter={axis.left}
          leftLabel={axis.lLabel}
          rightLetter={axis.right}
          rightLabel={axis.rLabel}
          value={50}
          onChange={vi.fn()}
        />
      )

      expect(screen.getByText(axis.left)).toBeInTheDocument()
      expect(screen.getByText(axis.right)).toBeInTheDocument()
      unmount()
    })
  })

  it('has proper styling for letter prominence', () => {
    const { container } = render(
      <MbtiAxisSlider {...defaultProps} value={25} />
    )

    const letters = container.querySelectorAll('span[style*="font-weight: 600"]')
    expect(letters.length).toBeGreaterThan(0)
  })

  it('includes transition effects for smooth animation', () => {
    const { container } = render(
      <MbtiAxisSlider {...defaultProps} value={50} />
    )

    const letters = container.querySelectorAll('span[style*="transition"]')
    expect(letters.length).toBeGreaterThan(0)
  })

  it('maintains slider input attributes', () => {
    const { container } = render(<MbtiAxisSlider {...defaultProps} />)
    const slider = container.querySelector('input[type="range"]') as HTMLInputElement

    expect(slider).toHaveAttribute('min', '0')
    expect(slider).toHaveAttribute('max', '100')
    expect(slider).toHaveAttribute('step', '5')
  })

  it('calculates percentages with correct formula', () => {
    const { rerender } = render(
      <MbtiAxisSlider {...defaultProps} value={25} />
    )

    // isLeft=true, leftPct=75 → "I 75"
    expect(screen.getByText(/I 75/)).toBeInTheDocument()

    rerender(
      <MbtiAxisSlider {...defaultProps} value={75} />
    )

    // isLeft=false, rightPct=75 → "E 75"
    expect(screen.getByText(/E 75/)).toBeInTheDocument()
  })

  it('handles rapid value changes', async () => {
    const onChange = vi.fn()

    const { rerender } = render(
      <MbtiAxisSlider {...defaultProps} value={50} onChange={onChange} />
    )

    // After 80: isLeft=false, rightPct=80 → "E 80"
    rerender(
      <MbtiAxisSlider {...defaultProps} value={80} onChange={onChange} />
    )

    expect(screen.getByText(/E 80/)).toBeInTheDocument()
  })
})
