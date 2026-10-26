const LABELS: Record<string, string> = {
  grNo: 'GR No.',
  surname: 'Surname',
  firstName: 'First name',
  middleName: 'Middle name',
  gender: 'Gender',
  dob: 'Date of birth',
  birthPlace: 'Place of birth',
  fatherName: 'Father',
  motherName: 'Mother',
  mobile1: 'Mobile 1',
  mobile2: 'Mobile 2',
  address: 'Address',
  aadhaar: 'Aadhaar',
  religionCaste: 'Religion / caste',
  admissionDate: 'Admission date',
  admissionStandardId: 'Admission standard',
  previousSchool: 'Previous school',
  categoryId: 'Category',
  standardId: 'Standard',
  divisionId: 'Division',
  status: 'Status',
  yearStatus: 'Year status',
  photo: 'Photo',
};

/** "dob" becomes "Date of birth"; a field without a label is spelled out from its name. */
export function fieldLabel(field: string): string {
  if (LABELS[field]) {
    return LABELS[field];
  }
  const words = field.replace(/([A-Z])/g, ' $1').trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function show(value: unknown): string {
  return value === null || value === undefined || value === '' ? '—' : String(value);
}

/**
 * One line per changed field for the History block: "Address: old → new" for an edit and "Address: value" for a
 * registration. Aadhaar and religion/caste arrive already masked from the server and are shown as they are.
 */
export function historyLines(changes: Record<string, unknown> | null): string[] {
  if (!changes) {
    return [];
  }
  const before = (changes['old'] ?? null) as Record<string, unknown> | null;
  const after = (changes['new'] ?? null) as Record<string, unknown> | null;
  if (!after) {
    return [];
  }
  return Object.keys(after).map((field) =>
    before && field in before
      ? `${fieldLabel(field)}: ${show(before[field])} → ${show(after[field])}`
      : `${fieldLabel(field)}: ${show(after[field])}`,
  );
}

/** "Student#12" is the student; "StudentEnrollment#40" is one of the class records. */
export function recordLabel(record: string): string {
  return record.startsWith('StudentEnrollment') ? 'Class record' : 'Student';
}
