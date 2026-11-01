import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdmissionsService } from '../admissions.service';
import { AdmissionsListComponent } from './admissions-list.component';

describe('AdmissionsListComponent', () => {
  let component: AdmissionsListComponent;
  let fixture: ComponentFixture<AdmissionsListComponent>;
  let mockService: {
    list: ReturnType<typeof vi.fn>;
    refreshPendingCount: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockService = {
      list: vi.fn().mockReturnValue(
        of({
          total: 2,
          rows: [
            {
              id: 1,
              studentName: 'Patil, Rahul',
              status: 'Pending',
              duplicateFlag: true,
              gender: 'Male',
              dob: '2015-06-10',
              mobile1: '9876543210',
              admissionStandard: 'Std 1',
            },
            {
              id: 2,
              studentName: 'Sharma, Sneha',
              status: 'Approved',
              duplicateFlag: false,
              gender: 'Female',
              dob: '2015-08-14',
              mobile1: '9811223344',
              admissionStandard: 'Std 1',
            },
          ],
        })
      ),
      refreshPendingCount: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdmissionsListComponent],
      providers: [
        provideRouter([]),
        { provide: AdmissionsService, useValue: mockService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdmissionsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component and loads admissions list', () => {
    expect(component).toBeTruthy();
    expect(mockService.list).toHaveBeenCalled();
    expect(component['rows']().length).toBe(2);
    expect(component['total']()).toBe(2);
  });

  it('filters by status', () => {
    component.setStatus('Pending');
    expect(component['statusFilter']()).toBe('Pending');
    expect(mockService.list).toHaveBeenCalledWith('Pending', undefined, 0, 25, '');
  });

  it('toggles duplicate only filter', () => {
    component.toggleDuplicates();
    expect(component['duplicateOnly']()).toBe(true);
    expect(mockService.list).toHaveBeenCalledWith('All', true, 0, 25, '');
  });
});
