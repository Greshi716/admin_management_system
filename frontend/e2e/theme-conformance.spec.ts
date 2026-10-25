import { expect, test } from '@playwright/test';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';

/**
 * Colours are defined once, in the @theme block of src/styles.css, and everything else uses the token classes
 * (bg-accent, text-muted, border-border, ...). This test reads the source and fails when a screen brings its own
 * colour, so the whole app keeps one look and the accent can change in one place (FR-043).
 */
const SRC = join(process.cwd(), 'src');

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return sourceFiles(path);
    }
    return /\.(ts|html|css)$/.test(entry.name) && !entry.name.endsWith('.spec.ts') ? [path] : [];
  });
}

/** A hex colour (#abc, #aabbcc, #aabbccdd), not an HTML entity or an anchor id. */
const HEX_COLOUR = /(?<![\w&])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})(?![\w-])/g;
const COLOUR_FUNCTION = /\b(?:rgb|rgba|hsl|hsla|oklch|oklab|lab|lch)\(/g;
/** Tailwind's built-in palette (bg-red-500, text-slate-700, ...), which is not part of the CampusFlow theme. */
const PALETTE_CLASS =
  /\b(?:bg|text|border|from|via|to|ring|fill|stroke|divide|outline|shadow|accent|caret|decoration)-(?:red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|slate|gray|zinc|neutral|stone)-\d{2,3}\b/g;

function withoutThemeBlock(css: string): string {
  return css.replace(/@theme\s*\{[^}]*\}/s, '');
}

function findings(pattern: RegExp, files: string[]): string[] {
  return files.flatMap((file) => {
    const text = file.endsWith('styles.css') ? withoutThemeBlock(readFileSync(file, 'utf8')) : readFileSync(file, 'utf8');
    return [...text.matchAll(pattern)].map((match) => `${relative(SRC, file)}: ${match[0]}`);
  });
}

test.describe('theme conformance', () => {
  const files = sourceFiles(SRC);

  test('FR-043: no screen or component hard-codes a hex colour', () => {
    expect(findings(HEX_COLOUR, files)).toEqual([]);
  });

  test('FR-043: no screen or component uses a colour function or a Tailwind palette colour', () => {
    expect([...findings(COLOUR_FUNCTION, files), ...findings(PALETTE_CLASS, files)]).toEqual([]);
  });

  test('FR-043: the accent colour is defined once in the theme block', () => {
    const css = readFileSync(join(SRC, 'styles.css'), 'utf8');

    expect(css.match(/--color-accent:\s*#6d4ae0\s*;/gi)).toHaveLength(1);
  });
});
