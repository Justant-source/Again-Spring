import { copyFile, mkdir, access } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const sharedPolicies = resolve(__dirname, '../../shared/docs/policies');
const dest = resolve(__dirname, '../public/legal');

await mkdir(dest, { recursive: true });

try {
  await access(sharedPolicies);
} catch {
  console.log('shared/docs/policies not found (Docker build) — skipping sync');
  process.exit(0);
}

const files = ['terms.md', 'privacy.md'];
for (const file of files) {
  await copyFile(resolve(sharedPolicies, file), resolve(dest, file));
  console.log(`synced: shared/docs/policies/${file} → frontend/public/legal/${file}`);
}
