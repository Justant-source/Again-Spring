const fs = require('fs');
const path = require('path');

const REQUIRED = {
  metaphors: [
    '01-locked-mailbox.svg', '02-boiling-kettle.svg', '03-locked-door.svg',
    '04-too-big-umbrella.svg', '05-person-in-rain.svg', '06-frozen-pond.svg',
    '07-cracked-window.svg', '08-empty-chair.svg', '09-overflowing-cup.svg',
    '10-rope-bridge.svg', '11-half-open-letter.svg', '12-two-trees-roots.svg',
  ],
  share: [
    'card-b-blurred-letter.svg', 'card-c-metaphor-frame.svg',
    'card-d-ratio-bar.svg', 'card-e-horsemen-mirror.svg',
    'card-shared-frame.svg',
  ],
  icons: [
    'icon-solo.svg', 'icon-heart-pause.svg',
    'icon-notebook.svg', 'icon-bridge.svg',
  ],
};

const ROOT = path.join(__dirname, '..', 'public');
let missing = [];
let stillPlaceholder = [];

const PLACEHOLDER_MARKER = 'PLACEHOLDER';

function check(folder, files) {
  files.forEach(f => {
    const fp = path.join(ROOT, folder, f);
    if (!fs.existsSync(fp)) {
      missing.push(`${folder}/${f}`);
      return;
    }
    const content = fs.readFileSync(fp, 'utf8');
    if (content.includes(PLACEHOLDER_MARKER)) {
      stillPlaceholder.push(`${folder}/${f}`);
    }
  });
}

check('illustrations/metaphors', REQUIRED.metaphors);
check('illustrations/share', REQUIRED.share);
check('icons/v2', REQUIRED.icons);

const total = REQUIRED.metaphors.length + REQUIRED.share.length + REQUIRED.icons.length;
console.log(`Total required: ${total}`);
console.log(`Missing: ${missing.length}`);
missing.forEach(f => console.log(`  ❌ ${f}`));
console.log(`Still placeholder: ${stillPlaceholder.length}`);
stillPlaceholder.forEach(f => console.log(`  ⚠️  ${f}`));

if (missing.length === 0 && stillPlaceholder.length === 0) {
  console.log('\n✅ All design assets are real (no placeholders).');
  process.exit(0);
}

if (missing.length > 0) {
  console.error('\n❌ Missing files. Cannot proceed to Step 2.');
  process.exit(1);
}

if (stillPlaceholder.length > 0) {
  console.warn('\n⚠️  Some files are still placeholders. Step 2 will work but use placeholders.');
  process.exit(0);
}
