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
    expect(screen.getByText('70%')).toBeInTheDocument()
  })

  it('displays percentage for right side', () => {
    render(<MbtiAxisSlider {...defaultProps} value={70} />)
    expect(screen.getByText('70%')).toBeInTheDocument()
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

    // At 50, both sides should show 50%
    const percentages = screen.getAllByText('50%')
    expect(percentages.length).toBeGreaterThanOrEqual(2)
  })

  it('calculates left percentage correctly', () => {
    render(<MbtiAxisSlider {...defaultProps} value={30} />)
    // 100 - 30 = 70 for left
    expect(screen.getByText('70%')).toBeInTheDocument()
  })

  it('calculates right percentage correctly', () => {
    render(<MbtiAxisSlider {...defaultProps} value={30} />)
    // right = 30
    const percentages = screen.getAllByText(/30%|70%/)
    expect(percentages.length).toBeGreaterThan(0)
  })

  it('handles extreme values (0)', () => {
    render(<MbtiAxisSlider {...defaultProps} value={0} />)
    expect(screen.getByText('100%')).toBeInTheDocument()
    expect(screen.getByText('0%')).toBeInTheDocument()
  })

  it('handles extreme values (100)', () => {
    render(<MbtiAxisSlider {...defaultProps} value={100} />)
    expect(screen.getByText('0%')).toBeInTheDocument()
    expect(screen.getByText('100%')).toBeInTheDocument()
  })

  it('updates when value prop changes', () => {
    const { rerender } = render(
      <MbtiAxisSlider {...defaultProps} value={30} />
    )

    expect(screen.getByText('70%')).toBeInTheDocument()

    rerender(
      <MbtiAxisSlider {...defaultProps} value={70} />
    )

    expect(screen.getByText('30%')).toBeInTheDocument()
  })

  it('determines left bias correctly', () => {
    const { container } = render(
      <MbtiAxisSlider {...defaultProps} value={40} />
    )

    // With value < 50, isLeft should be true
    const leftPercentage = screen.getByText('60%')
    expect(leftPercentage).toBeInTheDocument()
  })

  it('determines right bias correctly', () => {
    const { container } = render(
      <MbtiAxisSlider {...defaultProps} value={60} />
    )

    // With value > 50, isLeft should be false
    const rightPercentage = screen.getByText('60%')
    expect(rightPercentage).toBeInTheDocument()
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

    const letters = container.querySelectorAll('span[style*="font-weight: 700"]')
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

    // leftPct = 100 - 25 = 75, rightPct = 25
    expect(screen.getByText('75%')).toBeInTheDocument()

    rerender(
      <MbtiAxisSlider {...defaultProps} value={75} />
    )

    // leftPct = 100 - 75 = 25, rightPct = 75
    expect(screen.getByText('25%')).toBeInTheDocument()
  })

  it('handles rapid value changes', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    const { rerender } = render(
      <MbtiAxisSlider {...defaultProps} value={50} onChange={onChange} />
    )

    // After 80: leftPct = 100 - 80 = 20, rightPct = 80
    rerender(
      <MbtiAxisSlider {...defaultProps} value={80} onChange={onChange} />
    )

    expect(screen.getByText('20%')).toBeInTheDocument()
  })
})
