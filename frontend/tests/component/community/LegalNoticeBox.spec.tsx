import { render, screen } from '@testing-library/react';
import LegalNoticeBox from '@/components/shared/LegalNoticeBox';

describe('LegalNoticeBox', () => {
  it('항상 렌더됨', () => {
    render(<LegalNoticeBox />);
    expect(screen.getByTestId('ratio-legal-notice')).toBeInTheDocument();
    expect(screen.getByText(/과실 비율과 무관/)).toBeInTheDocument();
  });

  it('커스텀 메시지 표시', () => {
    render(<LegalNoticeBox message="공감 분포일 뿐입니다" />);
    expect(screen.getByText(/공감 분포일 뿐/)).toBeInTheDocument();
  });

  it('data-testid="ratio-legal-notice" 기본값', () => {
    render(<LegalNoticeBox />);
    expect(screen.getByTestId('ratio-legal-notice')).toBeInTheDocument();
  });

  it('커스텀 testId 지정 가능', () => {
    render(<LegalNoticeBox testId="custom-notice" />);
    expect(screen.getByTestId('custom-notice')).toBeInTheDocument();
  });

  it('data-testid 속성이 항상 존재', () => {
    const { container } = render(<LegalNoticeBox />);
    const noticeBox = container.querySelector('[data-testid="ratio-legal-notice"]');
    expect(noticeBox).toBeInTheDocument();
    expect(noticeBox?.getAttribute('data-testid')).toBe('ratio-legal-notice');
  });
});
