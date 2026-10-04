import { readFileSync } from 'node:fs';
import { validateEvidence } from './evidence-contract.mjs';

const files = process.argv.slice(2);
if (!files.length) {
  console.error('Usage: node scripts/validate-evidence.mjs <evidence.json> [...]');
  process.exitCode = 2;
} else {
  for (const file of files) {
    try {
      const errors = validateEvidence(JSON.parse(readFileSync(file, 'utf8')));
      if (errors.length) {
        console.error(`${file}: invalid\n${errors.join('\n')}`);
        process.exitCode = 1;
      } else console.log(`${file}: valid`);
    } catch (error) {
      console.error(`${file}: ${error.message}`);
      process.exitCode = 1;
    }
  }
}
