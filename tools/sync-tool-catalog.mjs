import {readFileSync, writeFileSync, mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const source = readFileSync(root + 'server/jarvis-ai-worker/src/tool-catalog.json', 'utf8');
const target = root + 'android/app/src/main/assets/tool-catalog.json';
// Android і Worker використовують той самий каталог. CI перевіряє відповідність.
if (process.argv.includes('--check')) {
  if (readFileSync(target, 'utf8') !== source) throw new Error('Run node tools/sync-tool-catalog.mjs');
} else {
  mkdirSync(root + 'android/app/src/main/assets', {recursive:true});
  writeFileSync(target, source);
}
