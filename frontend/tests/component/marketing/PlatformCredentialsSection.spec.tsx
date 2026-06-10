import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { PlatformCredentialsSection } from '@/components/admin/marketing/PlatformCredentialsSection';
import type { PlatformCredentialStatus } from '@/lib/api/admin/marketing';

vi.mock('@/lib/api/admin/marketing', () => ({
  listPlatformCredentials: vi.fn(),
  upsertPlatformCredential: vi.fn(),
  deletePlatformCredential: vi.fn(),
}));

import {
  listPlatformCredentials,
  upsertPlatformCredential,
  deletePlatformCredential,
} from '@/lib/api/admin/marketing';

const xUnconfigured: PlatformCredentialStatus = {
  platform: 'x',
  fields: [
    { key: 'handle', secret: false, required: true },
    { key: 'password', secret: true, required: true },
    { key: 'totp_secret', secret: true, required: false },
  ],
  configured: false,
  values: {},
  secret_set: { password: false, totp_secret: false },
  updated_at: null,
};

const naverConfigured: PlatformCredentialStatus = {
  platform: 'naver_blog',
  fields: [
    { key: 'naver_id', secret: false, required: true },
    { key: 'password', secret: true, required: true },
    { key: 'blog_id', secret: false, required: false },
  ],
  configured: true,
  values: { naver_id: 'myid', blog_id: 'myblog' },
  secret_set: { password: true },
  updated_at: '2026-06-09T00:00:00',
};

describe('PlatformCredentialsSection', () => {
  beforeEach(() => {
    vi.mocked(listPlatformCredentials).mockResolvedValue([xUnconfigured, naverConfigured]);
    vi.mocked(upsertPlatformCredential).mockResolvedValue(naverConfigured);
    vi.mocked(deletePlatformCredential).mockResolvedValue(undefined);
  });

  it('renders a card per platform with configured/unconfigured status and account', async () => {
    render(<PlatformCredentialsSection />);
    expect(await screen.findByText('X (트위터)')).toBeInTheDocument();
    expect(screen.getByText('네이버 블로그')).toBeInTheDocument();
    expect(screen.getByText('미설정')).toBeInTheDocument();
    expect(screen.getByText('설정됨')).toBeInTheDocument();
    expect(screen.getByText('myid')).toBeInTheDocument(); // configured account shown
  });

  it('opens the edit dialog with fields rendered dynamically from the schema', async () => {
    render(<PlatformCredentialsSection />);
    await screen.findByText('X (트위터)');
    fireEvent.click(screen.getByRole('button', { name: '계정 연결' })); // unconfigured X
    expect(await screen.findByText('핸들 / 이메일')).toBeInTheDocument();
    expect(screen.getByText('비밀번호')).toBeInTheDocument();
    expect(screen.getByText('2FA TOTP 시크릿')).toBeInTheDocument();
  });

  it('blocks save when required fields are empty', async () => {
    render(<PlatformCredentialsSection />);
    await screen.findByText('X (트위터)');
    fireEvent.click(screen.getByRole('button', { name: '계정 연결' }));
    await screen.findByText('핸들 / 이메일');
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText(/필수 항목을 입력해주세요/)).toBeInTheDocument();
    expect(upsertPlatformCredential).not.toHaveBeenCalled();
  });

  it('saves a configured platform and omits the blank secret (keep-existing)', async () => {
    render(<PlatformCredentialsSection />);
    await screen.findByText('네이버 블로그');
    fireEvent.click(screen.getByRole('button', { name: '편집' })); // configured naver
    await screen.findByText('네이버 아이디');
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(upsertPlatformCredential).toHaveBeenCalledTimes(1));
    const [platform, values] = vi.mocked(upsertPlatformCredential).mock.calls[0];
    expect(platform).toBe('naver_blog');
    expect(values).not.toHaveProperty('password'); // blank secret omitted
    expect(values).toMatchObject({ naver_id: 'myid', blog_id: 'myblog' });
  });
});
