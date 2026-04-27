import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ContributionRatio } from '@/components/result/ContributionRatio'
import { createReport } from '@/tests/fixtures'
import { vi } from 'vitest'

describe('ContributionRatio', () => {
  it('renders section title', () => {
    const ratio = { a: 55, b: 45, label: { a: 'User A text', b: 'User B text' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(screen.getByText('화해 기여도')).toBeInTheDocument()
  })

  it('displays both percentages for each user', () => {
    const ratio = { a: 60, b: 40, label: { a: 'User A text', b: 'User B text' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(screen.getByText(/Alice.*60/)).toBeInTheDocument()
    expect(screen.getByText(/Bob.*40/)).toBeInTheDocument()
  })

  it('displays user names with percentages', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Alice label', b: 'Bob label' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(screen.getByText('Alice label')).toBeInTheDocument()
    expect(screen.getByText('Bob label')).toBeInTheDocument()
  })

  it('shows legal disclaimer box with required text', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(
      screen.getByText(/이 수치는 두 분의 회복 시작점을 부드럽게 안내하기 위한 참고용이에요/)
    ).toBeInTheDocument()
    expect(
      screen.getByText(/법적 판단이나 과실 비율과는 무관/)
    ).toBeInTheDocument()
  })

  it('does NOT contain forbidden word "과실비율"', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    const { container } = render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(container.textContent).not.toContain('과실비율')
  })

  it('shows solo mode message when isSoloMode is true', () => {
    render(
      <ContributionRatio
        ratio={null}
        nameA="Alice"
        nameB="Bob"
        isSoloMode={true}
      />
    )

    expect(
      screen.getByText(/화해 기여도는 상대방이 함께/)
    ).toBeInTheDocument()
  })

  it('displays user name in solo mode message', () => {
    render(
      <ContributionRatio
        ratio={null}
        nameA="Alice"
        nameB="Bob"
        isSoloMode={true}
      />
    )

    expect(screen.getByText(/Alice님 한 분의 관점/)).toBeInTheDocument()
  })

  it('shows invite button in solo mode when onInvite is provided', () => {
    const onInvite = vi.fn()
    render(
      <ContributionRatio
        ratio={null}
        nameA="Alice"
        nameB="Bob"
        isSoloMode={true}
        onInvite={onInvite}
      />
    )

    expect(screen.getByRole('button', { name: /상대방 초대하기/ })).toBeInTheDocument()
  })

  it('calls onInvite when invite button is clicked', async () => {
    const user = userEvent.setup()
    const onInvite = vi.fn()
    render(
      <ContributionRatio
        ratio={null}
        nameA="Alice"
        nameB="Bob"
        isSoloMode={true}
        onInvite={onInvite}
      />
    )

    const inviteButton = screen.getByRole('button', { name: /상대방 초대하기/ })
    await user.click(inviteButton)

    expect(onInvite).toHaveBeenCalled()
  })

  it('does not render invite button when onInvite is not provided', () => {
    render(
      <ContributionRatio
        ratio={null}
        nameA="Alice"
        nameB="Bob"
        isSoloMode={true}
      />
    )

    expect(screen.queryByRole('button', { name: /상대방 초대하기/ })).not.toBeInTheDocument()
  })

  it('displays difference conflict type message', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
        conflictType="difference"
      />
    )

    expect(screen.getByText(/두 분 모두 잘못한 게 아니라 다를 뿐이에요/)).toBeInTheDocument()
  })

  it('displays factual conflict type message', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
        conflictType="factual"
      />
    )

    expect(screen.getByText(/이번 상황에서는 한쪽의 책임이 좀 더 분명해 보여요/)).toBeInTheDocument()
  })

  it('does not display conflict type message when it is null', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    const { container } = render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
        conflictType={null}
      />
    )

    expect(container.textContent).not.toContain('두 분 모두 잘못한 게')
    expect(container.textContent).not.toContain('한쪽의 책임이')
  })

  it('displays mixed conflict type message (none or mixed)', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    const { container } = render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
        conflictType="mixed"
      />
    )

    // Mixed type should not show specific message
    expect(container.textContent).not.toContain('두 분 모두 잘못한')
  })

  it('uses default names when not provided', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    const { container } = render(<ContributionRatio ratio={ratio} />)

    expect(container.textContent).toContain('서현')
    expect(container.textContent).toContain('준호')
  })

  it('renders null when no ratio provided and not solo mode', () => {
    const { container } = render(
      <ContributionRatio
        ratio={null}
        nameA="Alice"
        nameB="Bob"
        isSoloMode={false}
      />
    )

    expect(container.firstChild).toBeNull()
  })

  it('shows different ratio percentages correctly', () => {
    const ratio = { a: 70, b: 30, label: { a: 'A is more collaborative', b: 'B is less collaborative' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(screen.getByText(/Alice.*70/)).toBeInTheDocument()
    expect(screen.getByText(/Bob.*30/)).toBeInTheDocument()
  })

  it('shows equal ratio when both are 50%', () => {
    const ratio = { a: 50, b: 50, label: { a: 'Equal A', b: 'Equal B' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(screen.getByText(/Alice.*50/)).toBeInTheDocument()
    expect(screen.getByText(/Bob.*50/)).toBeInTheDocument()
  })

  it('contains mention of AI limitations', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(screen.getByText(/AI 분析에는 한계가 있어요/)).toBeInTheDocument()
  })

  it('recommends professional counseling', () => {
    const ratio = { a: 55, b: 45, label: { a: 'Text A', b: 'Text B' } }
    render(
      <ContributionRatio
        ratio={ratio}
        nameA="Alice"
        nameB="Bob"
      />
    )

    expect(screen.getByText(/깊은 갈등은 전문 상담을 권해드려요/)).toBeInTheDocument()
  })
})
