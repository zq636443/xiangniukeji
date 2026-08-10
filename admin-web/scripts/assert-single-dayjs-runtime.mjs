import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const assetsDirectory = new URL('../dist/assets/', import.meta.url);
const assetFiles = (await readdir(assetsDirectory)).filter((file) => file.endsWith('.js'));
const bundlesWithDayjs = [];

for (const assetFile of assetFiles) {
  const source = await readFile(join(fileURLToPath(assetsDirectory), assetFile), 'utf8');
  const runtimeCount = source.match(/\$isDayjsObject/g)?.length ?? 0;
  if (runtimeCount > 0) {
    bundlesWithDayjs.push({ assetFile, runtimeCount });
  }
}

const duplicatedBundles = bundlesWithDayjs.filter(({ runtimeCount }) => runtimeCount !== 1);
if (bundlesWithDayjs.length === 0 || duplicatedBundles.length > 0) {
  throw new Error(`Expected one Dayjs runtime per production bundle, found ${JSON.stringify(bundlesWithDayjs)}`);
}

console.log(`Dayjs production runtime count: 1 in ${bundlesWithDayjs.length} bundle(s)`);
