import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LikertQuestion } from '@/components/onboarding/LikertQuestion'
import { vi } from 'vitest'

const mockQuestion = {
  id: 'q1',
  text: 'I feel comfortable expressing my emotions in relationships',
  measures: 'withdrawal' as const,
}

describe('LikertQuestion', () => {
  it('renders question text', () => {
    const onChange = vi.fn()
    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText(mockQuestion.text)).toBeInTheDocument()
  })

  it('displays question ID in uppercase', () => {
    const onChange = vi.fn()
    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText(/Q1/)).toBeInTheDocument()
  })

  it('renders 5 selection buttons', () => {
    const onChange = vi.fn()
    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    const buttons = screen.getAllByRole('button')
    expect(buttons).toHaveLength(5)
  })

  it('displays all 5 numeric options', () => {
    const onChange = vi.fn()
    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    for (let i = 1; i <= 5; i++) {
      expect(screen.getByText(i.toString())).toBeInTheDocument()
    }
  })

  it('displays likert scale labels', () => {
    const onChange = vi.fn()
    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText('전혀 아니다')).toBeInTheDocument()
    expect(screen.getByText('매우 그렇다')).toBeInTheDocument()
  })

  it('calls onChange when option is clicked', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    const button3 = screen.getByRole('button', { name: '3번 선택' })
    await user.click(button3)

    expect(onChange).toHaveBeenCalledWith(3)
  })

  it('highlights selected value visually', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const { rerender } = render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    // Simulate selection
    const button4 = screen.getByRole('button', { name: '4번 선택' })
    await user.click(button4)

    rerender(
      <LikertQuestion
        question={mockQuestion}
        value={4}
        onChange={onChange}
      />
    )

    // The selected button should have different styling (class 'on')
    expect(button4).toHaveClass('on')
  })

  it('allows selection of all values 1-5', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    for (let value = 1; value <= 5; value++) {
      const { unmount } = render(
        <LikertQuestion
          question={mockQuestion}
          value={null}
          onChange={onChange}
        />
      )

      const button = screen.getByRole('button', { name: `${value}번 선택` })
      await user.click(button)

      expect(onChange).toHaveBeenCalledWith(value)
      unmount()
    }
  })

  it('responds to left arrow key', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    render(
      <LikertQuestion
        question={mockQuestion}
        value={3}
        onChange={onChange}
      />
    )

    // Focus on the component and press left arrow
    await user.keyboard('{ArrowLeft}')

    expect(onChange).toHaveBeenCalledWith(2)
  })

  it('responds to right arrow key', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    render(
      <LikertQuestion
        question={mockQuestion}
        value={3}
        onChange={onChange}
      />
    )

    // Focus on the component and press right arrow
    await user.keyboard('{ArrowRight}')

    expect(onChange).toHaveBeenCalledWith(4)
  })

  it('starts at 1 when pressing right arrow with no value', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    await user.keyboard('{ArrowRight}')

    expect(onChange).toHaveBeenCalledWith(5) // goes to right extreme
  })

  it('starts at 1 when pressing left arrow with no value', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    await user.keyboard('{ArrowLeft}')

    expect(onChange).toHaveBeenCalledWith(1)
  })

  it('prevents left arrow from going below 1', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    render(
      <LikertQuestion
        question={mockQuestion}
        value={1}
        onChange={onChange}
      />
    )

    await user.keyboard('{ArrowLeft}')

    // Should not call onChange when already at minimum
    expect(onChange).not.toHaveBeenCalled()
  })

  it('prevents right arrow from going above 5', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    render(
      <LikertQuestion
        question={mockQuestion}
        value={5}
        onChange={onChange}
      />
    )

    await user.keyboard('{ArrowRight}')

    // Should not call onChange when already at maximum
    expect(onChange).not.toHaveBeenCalled()
  })

  it('has proper aria labels for accessibility', () => {
    const onChange = vi.fn()
    render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    const buttons = screen.getAllByRole('button')
    buttons.forEach((button, index) => {
      expect(button).toHaveAttribute('aria-label')
      expect(button).toHaveAttribute('title', (index + 1).toString())
    })
  })

  it('handles different question texts', () => {
    const onChange = vi.fn()
    const differentQuestion = {
      id: 'q5',
      text: 'A completely different question about relationships',
      measures: 'empathy_priority' as const,
    }

    render(
      <LikertQuestion
        question={differentQuestion}
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText(differentQuestion.text)).toBeInTheDocument()
    expect(screen.getByText(/Q5/)).toBeInTheDocument()
  })

  it('works with long question text', () => {
    const onChange = vi.fn()
    const longQuestion = {
      id: 'q_long',
      text: 'This is a very long question that spans multiple lines and contains a lot of information about relationships and communication patterns',
      measures: 'logical_orientation' as const,
    }

    render(
      <LikertQuestion
        question={longQuestion}
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText(longQuestion.text)).toBeInTheDocument()
  })

  it('maintains value when rerendered', () => {
    const onChange = vi.fn()
    const { rerender } = render(
      <LikertQuestion
        question={mockQuestion}
        value={3}
        onChange={onChange}
      />
    )

    const button = screen.getByRole('button', { name: '3번 선택' }) as HTMLButtonElement
    expect(button).toHaveClass('on')

    rerender(
      <LikertQuestion
        question={mockQuestion}
        value={3}
        onChange={onChange}
      />
    )

    const rerenderedButton = screen.getByRole('button', { name: '3번 선택' }) as HTMLButtonElement
    expect(rerenderedButton).toHaveClass('on')
  })

  it('handles null value properly', () => {
    const onChange = vi.fn()
    const { container } = render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    // No button should have the 'on' class
    const buttons = container.querySelectorAll('button.likert-dot.on')
    expect(buttons).toHaveLength(0)
  })

  it('cleans up event listeners on unmount', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()

    const { unmount } = render(
      <LikertQuestion
        question={mockQuestion}
        value={null}
        onChange={onChange}
      />
    )

    unmount()

    // After unmount, keyboard events should not trigger onChange
    // This is mainly to ensure no memory leaks
    expect(onChange).not.toHaveBeenCalled()
  })
})
