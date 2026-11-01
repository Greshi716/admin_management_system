import { expect, test } from '@playwright/test';
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * The check that every requirement has a test (FR-036, SC-009) is itself tested: on the real repository it must pass
 * in strict mode, and on a small made-up repository with one untested requirement it must fail and name it.
 */
const REPO = join(process.cwd(), '..');
const SCRIPT = join(REPO, 'scripts', 'check-fr-coverage.sh');

function run(root: string, ...args: string[]) {
  const gitBash = 'C:\\Program Files\\Git\\bin\\bash.exe';
  const bash = process.platform === 'win32' && existsSync(gitBash) ? gitBash : 'bash';
  return spawnSync(bash, [join(root, 'scripts', 'check-fr-coverage.sh'), ...args], { encoding: 'utf8' });
}

test.describe('FR coverage check', () => {
  test('FR-036: every requirement of the foundation spec is covered, in strict mode', () => {
    const result = run(REPO, '--strict');

    expect(result.status, result.stdout + result.stderr).toBe(0);
    expect(result.stdout).toMatch(/FR coverage: (\d+) of \1 covered/);
  });

  test('FR-036: strict mode fails and names a requirement that no test mentions', () => {
    const root = mkdtempSync(join(tmpdir(), 'fr-check-'));
    try {
      mkdirSync(join(root, 'scripts'));
      cpSync(SCRIPT, join(root, 'scripts', 'check-fr-coverage.sh'));
      mkdirSync(join(root, 'specs', '001-foundation'), { recursive: true });
      writeFileSync(
        join(root, 'specs', '001-foundation', 'spec.md'),
        '- **FR-901**: First made-up requirement.\n- **FR-902**: Second made-up requirement.\n',
      );
      mkdirSync(join(root, 'frontend', 'e2e'), { recursive: true });
      writeFileSync(
        join(root, 'frontend', 'e2e', 'one.spec.ts'),
        "test('FR-901: the first one is tested', () => {});\n",
      );

      const strict = run(root, '--strict');
      const report = run(root);

      expect(strict.status).toBe(1);
      expect(strict.stdout).toContain('1 of 2 covered');
      expect(strict.stdout).toContain('Uncovered: FR-902');
      expect(report.status).toBe(0);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
