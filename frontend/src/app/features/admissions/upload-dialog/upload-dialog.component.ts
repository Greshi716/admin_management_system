import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, model, output, signal } from '@angular/core';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { AdmissionsService, ImportResult } from '../admissions.service';

@Component({
  selector: 'app-upload-dialog',
  imports: [CommonModule, DialogComponent],
  templateUrl: './upload-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UploadDialogComponent {
  private readonly admissionsService = inject(AdmissionsService);

  readonly visible = model<boolean>(false);
  readonly uploaded = output<ImportResult>();

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly uploading = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly result = signal<ImportResult | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      if (file.size > 5 * 1024 * 1024) {
        this.errorMessage.set('The file is larger than 5 MB.');
        this.selectedFile.set(null);
        return;
      }
      this.selectedFile.set(file);
      this.errorMessage.set(null);
      this.result.set(null);
    }
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file) return;

    this.uploading.set(true);
    this.errorMessage.set(null);

    this.admissionsService.upload(file).subscribe({
      next: (res) => {
        this.uploading.set(false);
        this.result.set(res);
        this.uploaded.emit(res);
      },
      error: (err: HttpErrorResponse) => {
        this.uploading.set(false);
        if (err.error?.fieldErrors && err.error.fieldErrors.length > 0) {
          this.errorMessage.set(err.error.fieldErrors.map((f: { message: string }) => f.message).join(' '));
        } else if (err.error?.detail) {
          this.errorMessage.set(err.error.detail);
        } else {
          this.errorMessage.set('Could not upload responses file. Please verify file format.');
        }
      },
    });
  }

  close(): void {
    this.visible.set(false);
    this.selectedFile.set(null);
    this.errorMessage.set(null);
    this.result.set(null);
  }
}
