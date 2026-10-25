import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { AuthStore } from '../../../core/auth/auth.store';
import { ChangePasswordDialogComponent } from './change-password-dialog.component';

const CHANGE = '/api/auth/change-password';

/** The protected members the spec drives directly. */
interface Exposed {
  form: FormGroup;
  submit(): void;
}

describe('ChangePasswordDialogComponent (FR-009)', () => {
  let fixture: ComponentFixture<ChangePasswordDialogComponent>;
  let controller: HttpTestingController;
  let component: Exposed;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MessageService],
    });
    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangePasswordDialogComponent);
    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();
    component = fixture.componentInstance as unknown as Exposed;
  });

  afterEach(() => controller.verify());

  function fill(current: string, next: string, confirm: string): void {
    component.form.setValue({ currentPassword: current, newPassword: next, confirmPassword: confirm });
  }

  it('does not call the server for a password shorter than 8 characters', () => {
    fill('old-password', 'short', 'short');

    component.submit();

    controller.expectNone(CHANGE);
    expect(component.form.get('newPassword')?.hasError('minlength')).toBe(true);
  });

  it('does not call the server when the two new passwords differ', () => {
    fill('old-password', 'new-password-1', 'new-password-2');

    component.submit();

    controller.expectNone(CHANGE);
    expect(component.form.hasError('mismatch')).toBe(true);
  });

  it('does not call the server when the new password equals the current one', () => {
    fill('same-password', 'same-password', 'same-password');

    component.submit();

    controller.expectNone(CHANGE);
    expect(component.form.hasError('same')).toBe(true);
  });

  it('sends the change, remembers it and closes', () => {
    TestBed.inject(AuthStore).login('testadmin', 'x').subscribe();
    controller.expectOne('/api/auth/login').flush({
      name: 'T', username: 'testadmin', role: 'ADMIN', passwordChanged: false, currentAcademicYear: null, idleTimeoutSeconds: 600,
    });
    fill('old-password', 'new-password-1', 'new-password-1');

    component.submit();

    const request = controller.expectOne(CHANGE);
    expect(request.request.body).toEqual({ currentPassword: 'old-password', newPassword: 'new-password-1' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(TestBed.inject(AuthStore).passwordChanged()).toBe(true);
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('shows the server message beside the field when the current password is wrong', () => {
    fill('wrong-password', 'new-password-1', 'new-password-1');

    component.submit();

    controller.expectOne(CHANGE).flush(
      { fieldErrors: [{ field: 'currentPassword', message: 'The current password is not correct.' }] },
      { status: 422, statusText: 'Unprocessable' },
    );
    fixture.detectChanges();

    expect(component.form.get('currentPassword')?.errors?.['server']).toBe('The current password is not correct.');
    expect(fixture.componentInstance.visible()).toBe(true);
  });
});
