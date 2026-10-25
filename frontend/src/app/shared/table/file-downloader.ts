import { Injectable } from '@angular/core';

/** Hands a file to the browser as a download. Its own service so tests can replace it. */
@Injectable({ providedIn: 'root' })
export class FileDownloader {
  save(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }
}

/** The file name from a `Content-Disposition: attachment; filename="x.xlsx"` header, or the fallback. */
export function fileNameFrom(disposition: string | null, fallback: string): string {
  const match = disposition ? /filename="?([^";]+)"?/i.exec(disposition) : null;
  return match ? match[1] : fallback;
}
