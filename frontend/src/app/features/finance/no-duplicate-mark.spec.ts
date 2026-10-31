// @ts-ignore node:fs has no type declarations in the browser-oriented spec config; the test runs under Node.
import { readdirSync, readFileSync, statSync } from 'node:fs';

/**
 * FR-018: a reprint is identical to the first print. Nothing in the finance screens may carry a DUPLICATE mark, so a
 * scan of their source finds the word only as part of the receipt-number refusal code DUPLICATE_RECEIPT_NO.
 */
describe('no DUPLICATE mark in the finance screens (FR-018)', () => {
  const root = 'src/app/features/finance';

  function sources(directory: string): string[] {
    return readdirSync(directory).flatMap((name: string) => {
      const path = `${directory}/${name}`;
      if (statSync(path).isDirectory()) {
        return sources(path);
      }
      return /\.(ts|html)$/.test(name) && !name.endsWith('.spec.ts') ? [path] : [];
    });
  }

  it('finds no DUPLICATE other than the DUPLICATE_RECEIPT_NO code', () => {
    const files = sources(root);
    expect(files.length).toBeGreaterThan(5);

    const offending = files.filter((file) => /DUPLICATE(?!_RECEIPT_NO)/.test(readFileSync(file, 'utf8')));

    expect(offending).toEqual([]);
  });
});
