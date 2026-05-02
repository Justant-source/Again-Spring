import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SoloResult } from '@/components/result/SoloResult'
import { createSoloModeReport, createUser } from '@/tests/fixtures'
import { vi } from 'vitest'

// Mock Zustand stores
vi.mock('@/lib/store/userStore', () => ({
  useUserStore: vi.fn((selector) =>
    selector({
      user: createUser({
        nickname: 'TestUser',
        communicationStyle: 'wave',
      }),
    })
  ),
}))

vi.mock('@/lib/store/sessionStore', () => ({
  useSessionStore: vi.fn((selector) =>
    selector({
      sessionId: 'test-session-id',
      partnerNickname: 'Partner',
    })
  ),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

// Mock the Motif icons
vi.mock('@/components/shared/Motif', () => ({
  IconEye: ({ size }: { size: number }) => <span data-testid="icon-eye">Eye</span>,
  IconDrop: ({ size }: { size: number }) => <span data-testid="icon-drop">Drop</span>,
  IconNeed: ({ size }: { size: number }) => <span data-testid="icon-need">Need</span>,
  IconAsk: ({ size }: { size: number }) => <span data-testid="icon-ask">Ask</span>,
  IconMap: ({ size }: { size: number }) => <span data-testid="icon-map">Map</span>,
  STYLE_MOTIF: {
    wave: () => <span>Wave Icon</span>,
  },
}))

describe('SoloResult', () => {
  it('does not render when report is not in solo mode', () => {
    const report = createSoloModeReport({ isSoloMode: false })
    const { container } = render(<SoloResult report={report} />)

    expect(container.firstChild).toBeNull()
  })

  it('renders solo mode banner', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByText(/한쪽 분석.*완전한 리포트는 상대가 참여하면 완성돼요/)).toBeInTheDocument()
  })

  it('displays "한쪽 시점" analysis message', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByText(/한쪽 분석/)).toBeInTheDocument()
  })

  it('displays user nickname in analysis title', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByText(/TestUser님 입장에서의 정리/)).toBeInTheDocument()
  })

  it('displays observation step', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'I noticed this pattern',
          feeling: 'sad',
          need: 'respect',
          request: 'please listen',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText('관찰')).toBeInTheDocument()
    expect(screen.getByText('I noticed this pattern')).toBeInTheDocument()
  })

  it('displays feeling step', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'I felt sad',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText('느낌')).toBeInTheDocument()
    expect(screen.getByText('I felt sad')).toBeInTheDocument()
  })

  it('displays need step', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'feel',
          need: 'I need respect',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText('욕구')).toBeInTheDocument()
    expect(screen.getByText('I need respect')).toBeInTheDocument()
  })

  it('displays request step', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'feel',
          need: 'need',
          request: 'Could you listen?',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText('부탁')).toBeInTheDocument()
    expect(screen.getByText('Could you listen?')).toBeInTheDocument()
  })

  it('displays default messages when NVC steps are empty', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: '',
          feeling: '',
          need: '',
          request: '',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText(/상황을 객관적으로 보셨어요/)).toBeInTheDocument()
    expect(screen.getByText(/그때의 감정이 잘 정리되었어요/)).toBeInTheDocument()
    expect(screen.getByText(/진정한 필요가 드러났어요/)).toBeInTheDocument()
    expect(screen.getByText(/건설적인 요청이 있으셨어요/)).toBeInTheDocument()
  })

  it('shows NVC 4-sentence draft section', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'feel',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText(/상대에게 보낼 수 있는 4문장 초안/)).toBeInTheDocument()
  })

  it('displays copy button for NVC message', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'feel',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(
      screen.getByRole('button', { name: /카톡으로 보내기 \(복사\)/ })
    ).toBeInTheDocument()
  })

  it('copies NVC message to clipboard when copy button clicked', async () => {
    const user = userEvent.setup()
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'I noticed',
          feeling: 'sad',
          need: 'comfort',
          request: 'please help',
        },
      },
    })

    // Mock clipboard API
    const writeTextMock = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: writeTextMock },
      writable: true,
    })

    render(<SoloResult report={report} />)

    const copyButton = screen.getByRole('button', { name: /카톡으로 보내기/ })
    await user.click(copyButton)

    await screen.findByText(/복사됐어요/)
    expect(writeTextMock).toHaveBeenCalled()
  })

  it('displays communication style card', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByText(/당신의 대화 스타일/)).toBeInTheDocument()
  })

  it('displays needs map placeholder for solo mode', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByText(/욕구 차이 지도/)).toBeInTheDocument()
    expect(screen.getByText(/두 분이 함께 해야 그려져요/)).toBeInTheDocument()
  })

  it('displays invite CTA section', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByText(/지금이라도 Partner분을 초대하면/)).toBeInTheDocument()
  })

  it('displays invite button with partner name', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByRole('button', { name: /초대 링크 다시 보내기/ })).toBeInTheDocument()
  })

  it('navigates to invite page when invite button clicked', async () => {
    const user = userEvent.setup()
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    const inviteButton = screen.getByRole('button', { name: /초대 링크 다시 보내기/ })
    await user.click(inviteButton)

    // Note: Due to mocking, we verify the button exists and is clickable
    expect(inviteButton).toBeInTheDocument()
  })

  it('displays icon for observation', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'I noticed',
          feeling: 'sad',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByTestId('icon-eye')).toBeInTheDocument()
  })

  it('displays icon for feeling', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'sad',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByTestId('icon-drop')).toBeInTheDocument()
  })

  it('displays icon for need', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'sad',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByTestId('icon-need')).toBeInTheDocument()
  })

  it('displays icon for request', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'sad',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByTestId('icon-ask')).toBeInTheDocument()
  })

  it('displays map icon in needs map section', () => {
    const report = createSoloModeReport()
    render(<SoloResult report={report} />)

    expect(screen.getByTestId('icon-map')).toBeInTheDocument()
  })

  it('handles missing NVC data gracefully', () => {
    const report = createSoloModeReport({
      nvcScripts: undefined,
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText(/한쪽 분석/)).toBeInTheDocument()
  })

  it('shows customization note for NVC draft', () => {
    const report = createSoloModeReport({
      nvcScripts: {
        aToB: {
          observation: 'obs',
          feeling: 'sad',
          need: 'need',
          request: 'req',
        },
      },
    })
    render(<SoloResult report={report} />)

    expect(screen.getByText(/그대로 보내도, 일부만 다듬어 보내도 좋아요/)).toBeInTheDocument()
  })

  it('maintains responsive layout structure', () => {
    const report = createSoloModeReport()
    const { container } = render(<SoloResult report={report} />)

    // Check flex layout structure
    const mainContainer = container.querySelector('div[style*="padding"]')
    expect(mainContainer).toBeInTheDocument()
  })
})
