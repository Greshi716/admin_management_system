import { readFileSync } from 'node:fs';
import { inflateRawSync } from 'node:zlib';

/**
 * Reads the first sheet of an .xlsx file as rows of text, without a library (an .xlsx file is a zip of XML files).
 * Enough for the files CampusFlow exports: text, numbers and booleans, empty cells left out. Booleans come back as
 * TRUE or FALSE.
 */
export function readFirstSheet(path: string): string[][] {
  const zip = readFileSync(path);
  const sheet = entryText(zip, 'xl/worksheets/sheet1.xml');
  const shared = entryTextOrNull(zip, 'xl/sharedStrings.xml');
  const sharedStrings = shared ? [...shared.matchAll(/<si>(.*?)<\/si>/gs)].map((si) => textOf(si[1])) : [];

  return [...sheet.matchAll(/<row\b[^>]*>(.*?)<\/row>/gs)].map((row) => {
    const cells: string[] = [];
    for (const cell of row[1].matchAll(/<c\b([^>]*?)(?:\/>|>(.*?)<\/c>)/gs)) {
      const attributes = cell[1];
      const body = cell[2] ?? '';
      const reference = /\br="([A-Z]+)\d+"/.exec(attributes)?.[1] ?? 'A';
      const type = /\bt="(\w+)"/.exec(attributes)?.[1];
      const value = /<v>(.*?)<\/v>/s.exec(body)?.[1] ?? '';
      let text: string;
      if (type === 'inlineStr') {
        text = textOf(body);
      } else if (type === 's') {
        text = sharedStrings[Number(value)] ?? '';
      } else if (type === 'b') {
        text = value === '1' ? 'TRUE' : 'FALSE';
      } else {
        text = decode(value);
      }
      cells[columnIndex(reference)] = text;
    }
    return Array.from(cells, (cell) => cell ?? '');
  });
}

function columnIndex(letters: string): number {
  return [...letters].reduce((index, letter) => index * 26 + (letter.charCodeAt(0) - 64), 0) - 1;
}

function textOf(xml: string): string {
  return [...xml.matchAll(/<t\b[^>]*>(.*?)<\/t>/gs)].map((part) => decode(part[1])).join('');
}

function decode(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function entryText(zip: Buffer, name: string): string {
  const text = entryTextOrNull(zip, name);
  if (text === null) {
    throw new Error(`${name} is not in the .xlsx file`);
  }
  return text;
}

/** Finds a file in the zip through its central directory, which always has the real sizes. */
function entryTextOrNull(zip: Buffer, name: string): string | null {
  const end = zip.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  const entries = zip.readUInt16LE(end + 10);
  let offset = zip.readUInt32LE(end + 16);
  for (let i = 0; i < entries; i++) {
    const method = zip.readUInt16LE(offset + 10);
    const compressedSize = zip.readUInt32LE(offset + 20);
    const nameLength = zip.readUInt16LE(offset + 28);
    const extraLength = zip.readUInt16LE(offset + 30);
    const commentLength = zip.readUInt16LE(offset + 32);
    const localOffset = zip.readUInt32LE(offset + 42);
    const entryName = zip.toString('utf8', offset + 46, offset + 46 + nameLength);
    if (entryName === name) {
      const dataStart = localOffset + 30 + zip.readUInt16LE(localOffset + 26) + zip.readUInt16LE(localOffset + 28);
      const data = zip.subarray(dataStart, dataStart + compressedSize);
      return (method === 8 ? inflateRawSync(data) : data).toString('utf8');
    }
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return null;
}
