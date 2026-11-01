import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdmissionsService, ImportResult } from '../admissions.service';
import { UploadDialogComponent } from './upload-dialog.component';

describe('UploadDialogComponent', () => {
  let component: UploadDialogComponent;
  let fixture: ComponentFixture<UploadDialogComponent>;
  let mockAdmissionsService: { upload: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockAdmissionsService = {
      upload: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [UploadDialogComponent],
      providers: [{ provide: AdmissionsService, useValue: mockAdmissionsService }],
    }).compileComponents();

    fixture = TestBed.createComponent(UploadDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('rejects file larger than 5 MB', () => {
    const largeFile = new File(['x'.repeat(6 * 1024 * 1024)], 'large.xlsx');
    component.onFileSelected({ target: { files: [largeFile] } } as unknown as Event);

    expect(component['errorMessage']()).toContain('larger than 5 MB');
    expect(component['selectedFile']()).toBeNull();
  });

  it('accepts valid file and uploads successfully', () => {
    const file = new File(['valid'], 'sample.xlsx');
    component.onFileSelected({ target: { files: [file] } } as unknown as Event);
    expect(component['selectedFile']()).toBe(file);

    const mockResult: ImportResult = {
      batchId: 1,
      total: 10,
      added: 8,
      skipped: 1,
      duplicates: 2,
      invalid: [{ row: 5, reason: 'Invalid mobile' }],
    };
    mockAdmissionsService.upload.mockReturnValue(of(mockResult));

    const emitSpy = vi.spyOn(component.uploaded, 'emit');
    component.upload();

    expect(mockAdmissionsService.upload).toHaveBeenCalledWith(file);
    expect(component['result']()).toEqual(mockResult);
    expect(emitSpy).toHaveBeenCalledWith(mockResult);
  });

  it('handles upload error from server', () => {
    const file = new File(['bad'], 'bad.xlsx');
    component.onFileSelected({ target: { files: [file] } } as unknown as Event);

    mockAdmissionsService.upload.mockReturnValue(
      throwError(() => ({
        error: {
          fieldErrors: [{ field: 'file', message: 'Missing required columns: Mobile 1' }],
        },
      }))
    );

    component.upload();
    expect(component['errorMessage']()).toContain('Missing required columns: Mobile 1');
  });
});
