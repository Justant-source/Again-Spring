/**
 * OG 카드용 Noto Sans KR 폰트 로더 (서버 전용, 모듈 캐시).
 *
 * Satori(next/og 내장)는 시스템 폰트 접근 불가 — 한글 렌더링에 명시적 ArrayBuffer 필수.
 * readFile + 모듈 캐시로 컨테이너당 1회만 읽음(512M 한도 대응).
 */
import 'server-only';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

interface FontCache {
  regular: ArrayBuffer;
  bold: ArrayBuffer;
}

// 모듈 캐시 — Node 프로세스 재시작 전까지 유지
let cache: FontCache | null = null;

/**
 * Noto Sans KR Regular(400) + Bold(700) ArrayBuffer 반환.
 * 최초 호출 시 fs.readFile, 이후는 캐시 반환.
 *
 * 폰트 파일 위치 우선순위:
 *   1. process.cwd()/lib/og/fonts/ (빌드 출력에 포함된 경우)
 *   2. process.cwd()/public/fonts/ (Dockerfile COPY public/ 보장)
 */
export async function loadOgFonts(): Promise<FontCache> {
  if (cache) return cache;

  // 후보 디렉토리 순서대로 시도
  const candidates = [
    path.join(process.cwd(), 'lib', 'og', 'fonts'),
    path.join(process.cwd(), 'public', 'fonts'),
  ];

  let dir: string | null = null;
  for (const candidate of candidates) {
    try {
      await readFile(path.join(candidate, 'NotoSansKR-Regular.subset.ttf'), { flag: 'r' }).then(() => {});
      dir = candidate;
      break;
    } catch {
      // 없으면 다음 후보
    }
  }

  if (!dir) {
    throw new Error(
      '[loadOgFonts] NotoSansKR-Regular.subset.ttf 를 찾을 수 없습니다. ' +
        'lib/og/fonts/ 또는 public/fonts/ 에 폰트를 배치해 주세요.',
    );
  }

  const [regular, bold] = await Promise.all([
    readFile(path.join(dir, 'NotoSansKR-Regular.subset.ttf')),
    readFile(path.join(dir, 'NotoSansKR-Bold.subset.ttf')),
  ]);

  // Buffer → ArrayBuffer (정확한 바이트 범위 슬라이스)
  cache = {
    regular: regular.buffer.slice(regular.byteOffset, regular.byteOffset + regular.byteLength),
    bold: bold.buffer.slice(bold.byteOffset, bold.byteOffset + bold.byteLength),
  };

  return cache;
}
