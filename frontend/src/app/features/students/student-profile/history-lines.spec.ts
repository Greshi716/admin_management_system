import { fieldLabel, historyLines, recordLabel } from './history-lines';

describe('historyLines (FR-019)', () => {
  it('shows old and new value for an edit', () => {
    expect(historyLines({ old: { address: 'A' }, new: { address: 'B' } })).toEqual(['Address: A → B']);
  });

  it('shows the values of a registration', () => {
    expect(historyLines({ new: { grNo: 'GR-1', dob: '2012-01-01' } })).toEqual(['GR No.: GR-1', 'Date of birth: 2012-01-01']);
  });

  it('shows masked sensitive values exactly as the server sent them', () => {
    expect(historyLines({ old: { aadhaar: '********1098' }, new: { aadhaar: '********3333' } })).toEqual([
      'Aadhaar: ********1098 → ********3333',
    ]);
  });

  it('shows a dash for an empty value and nothing when there are no changes', () => {
    expect(historyLines({ old: { birthPlace: null }, new: { birthPlace: 'Test Town' } })).toEqual([
      'Place of birth: — → Test Town',
    ]);
    expect(historyLines(null)).toEqual([]);
    expect(historyLines({})).toEqual([]);
  });

  it('spells out a field that has no label', () => {
    expect(fieldLabel('someNewField')).toBe('Some new field');
  });

  it('tells the student from a class record', () => {
    expect(recordLabel('Student#3')).toBe('Student');
    expect(recordLabel('StudentEnrollment#40')).toBe('Class record');
  });
});
