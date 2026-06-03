import { render, screen, within } from '@testing-library/react';
import { JurySection } from '@/components/community/c3/JurySection';
import type { JuryResult } from '@/lib/api/community/postApi';

// ─────────────────────────────────────────────────────────────────
// 테스트 픽스처
// ─────────────────────────────────────────────────────────────────

function makeJuror(chosenOptionLabel: string, empathyComment: string) {
  return { ageGroup: '30대', gender: '여성', chosenOptionLabel, empathyComment };
}

const COMPLETE_JURY: JuryResult = {
  jurors: [
    makeJuror('작성자', '작성자 입장에서 충분히 공감됩니다. 상대방도 나름의 이유가 있어 보입니다.'),
    makeJuror('작성자', '작성자의 노력이 느껴집니다. 상대방 관점도 이해는 됩니다.'),
    makeJuror('상대방', '상대방 입장이 더 설득력 있습니다. 다만 작성자도 이해가 안 가는 건 아니에요.'),
  ],
  distribution: [
    { label: '작성자', count: 2, percentage: 66.7 },
    { label: '상대방', count: 1, percentage: 33.3 },
  ],
  legalNotice: '이 결과는 공감 분포일 뿐 법적 책임이나 과실 비율과 무관합니다.',
  // summaryLine 생략 — FE 계산 경로 검증
};

// ─────────────────────────────────────────────────────────────────
// 테스트
// ─────────────────────────────────────────────────────────────────

describe('JurySection', () => {
  it('jurorCount=0이면 아무것도 렌더 안 함', () => {
    const { container } = render(<JurySection jury={null} jurorCount={0} />);
    expect(container.firstChild).toBeNull();
  });

  it('jury=null이고 jurorCount>0이면 대기 상태 표시', () => {
    render(<JurySection jury={null} jurorCount={3} />);
    expect(screen.getByTestId('jury-section')).toBeInTheDocument();
    expect(screen.getByTestId('jury-pending')).toBeInTheDocument();
    expect(screen.getByText(/사연을 읽고 있어요/)).toBeInTheDocument();
  });

  it('jurors.length < jurorCount이면 대기 상태 (부분 도착)', () => {
    const partial: JuryResult = {
      jurors: [makeJuror('작성자', '댓글 하나만 도착')],
      distribution: [{ label: '작성자', count: 1, percentage: 100 }],
      legalNotice: '',
    };
    render(<JurySection jury={partial} jurorCount={3} />);
    expect(screen.getByTestId('jury-pending')).toBeInTheDocument();
  });

  it('완료 상태: 배심원 카드 3개, 요약 줄, 법적 고지 표시', () => {
    render(<JurySection jury={COMPLETE_JURY} jurorCount={3} />);

    // 섹션 컨테이너
    expect(screen.getByTestId('jury-section')).toBeInTheDocument();

    // 배심원 카드 3개
    const cards = screen.getAllByTestId('juror-card');
    expect(cards).toHaveLength(3);

    // 각 카드에 empathyComment 텍스트 포함
    expect(screen.getByText(/작성자 입장에서 충분히 공감됩니다/)).toBeInTheDocument();
    expect(screen.getByText(/작성자의 노력이 느껴집니다/)).toBeInTheDocument();
    expect(screen.getByText(/상대방 입장이 더 설득력 있습니다/)).toBeInTheDocument();

    // summaryLine 없으면 FE 계산: "3인 중 2인이 작성자에 공감"
    expect(screen.getByTestId('jury-summary')).toHaveTextContent('3인 중 2인이 작성자에 공감했어요');

    // 공감 분포 바 래퍼
    expect(screen.getByTestId('jury-distribution-bar')).toBeInTheDocument();

    // 법적 고지
    expect(screen.getByTestId('jury-legal-notice')).toBeInTheDocument();
    expect(screen.getByText(/법적 책임이나 과실 비율과 무관/)).toBeInTheDocument();
  });

  it('summaryLine이 BE에서 오면 우선 사용', () => {
    const withSummary: JuryResult = {
      ...COMPLETE_JURY,
      summaryLine: 'AI 배심원 3인 중 2인이 작성자에 공감했어요',
    };
    render(<JurySection jury={withSummary} jurorCount={3} />);
    expect(screen.getByTestId('jury-summary')).toHaveTextContent(
      'AI 배심원 3인 중 2인이 작성자에 공감했어요'
    );
  });

  it("chosenOptionLabel='상대방'인 카드는 lens '상대방에 공감' 표시", () => {
    render(<JurySection jury={COMPLETE_JURY} jurorCount={3} />);
    const cards = screen.getAllByTestId('juror-card');
    // 세 번째 카드(상대방 공감)에 "상대방에 공감" 렌즈 텍스트
    expect(within(cards[2]).getByText(/상대방에 공감/)).toBeInTheDocument();
  });

  it("chosenOptionLabel='작성자'인 카드는 lens '작성자에 공감' 표시", () => {
    render(<JurySection jury={COMPLETE_JURY} jurorCount={3} />);
    const cards = screen.getAllByTestId('juror-card');
    // 첫 번째 카드(작성자 공감)
    expect(within(cards[0]).getByText(/작성자에 공감/)).toBeInTheDocument();
  });

  it('헤더에 jurorCount가 표시됨', () => {
    render(<JurySection jury={COMPLETE_JURY} jurorCount={3} />);
    expect(screen.getByText('AI 배심원 3인의 시선')).toBeInTheDocument();
  });
});
