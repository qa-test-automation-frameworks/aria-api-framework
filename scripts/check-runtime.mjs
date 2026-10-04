import { readFileSync } from 'node:fs';

const pin = readFileSync(new URL('../.nvmrc', import.meta.url), 'utf8').trim();
const expected = pin.split('.').map(Number);
const actual = /^(\d+)\.(\d+)\.(\d+)$/.exec(process.versions.node)?.slice(1).map(Number);
if (
  !/^\d+\.\d+\.\d+$/.test(pin) ||
  !actual ||
  actual[0] !== expected[0] ||
  actual[1] < expected[1] ||
  (actual[1] === expected[1] && actual[2] < expected[2])
) {
  throw new Error(`Use Node.js ${pin} from .nvmrc (or a newer stable Node24 patch/minor); detected ${process.version}. No npm installation is required for these scripts.`);
}
