/** The signed-in user (contracts/openapi.yaml `Me`). */
export interface Me {
  name: string;
  username: string;
  role: 'ADMIN';
  passwordChanged: boolean;
  /** Null when today falls in no academic year (FR-013). */
  currentAcademicYear: string | null;
  idleTimeoutSeconds: number;
}
