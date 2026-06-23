import fs from 'fs'

const CANDIDATE_PATHS = [
  process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
  '/home/justant/snap/codex/34/.cache/ms-playwright/chromium-1217/chrome-linux64/chrome',
  '/home/justant/snap/codex/34/.cache/ms-playwright/chromium_headless_shell-1217/chrome-headless-shell-linux64/chrome-headless-shell',
]

const LOCAL_LIB_DIR = '/home/justant/Data/Again-Spring/.playwright-libs/root/usr/lib/x86_64-linux-gnu'

export function resolveChromiumExecutablePath(): string | undefined {
  for (const candidate of CANDIDATE_PATHS) {
    if (candidate && fs.existsSync(candidate)) return candidate
  }
  return undefined
}

export function chromiumLaunchOptions(): {
  executablePath?: string
  channel?: string
  env: NodeJS.ProcessEnv
} {
  const executablePath = resolveChromiumExecutablePath()
  const env = {
    ...process.env,
    LD_LIBRARY_PATH: [
      LOCAL_LIB_DIR,
      '/lib/x86_64-linux-gnu',
      '/usr/lib/x86_64-linux-gnu',
      process.env.LD_LIBRARY_PATH,
    ]
      .filter(Boolean)
      .join(':'),
  }
  if (executablePath) return { executablePath, env }
  return { env }
}
