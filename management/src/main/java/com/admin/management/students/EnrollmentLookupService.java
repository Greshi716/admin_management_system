package com.admin.management.students;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.students.api.EnrollmentLookup;

/** Implements the public class record lookup over this module's repository. */
@Service
@Transactional(readOnly = true)
class EnrollmentLookupService implements EnrollmentLookup {

	private final EnrollmentRepository enrollments;

	private final MasterDataLookup masterData;

	EnrollmentLookupService(EnrollmentRepository enrollments, MasterDataLookup masterData) {
		this.enrollments = enrollments;
		this.masterData = masterData;
	}

	@Override
	public List<ClassRecordRef> classRecordsOfYear(Long yearId) {
		return enrollments.findByAcademicYearIdOrderById(yearId).stream().map(EnrollmentLookupService::ref).toList();
	}

	@Override
	public long countOfYear(Long yearId) {
		return enrollments.countByAcademicYearId(yearId);
	}

	@Override
	public Optional<ClassRecordRef> shownRecord(Long studentId) {
		List<StudentEnrollment> records = enrollments.findByStudentId(studentId);
		Long currentYearId = masterData.currentYear().map(MasterDataLookup.YearRef::id).orElse(null);
		return records.stream()
			.filter(record -> record.getAcademicYearId().equals(currentYearId))
			.findFirst()
			.or(() -> records.stream().max(Comparator.comparing(StudentEnrollment::getYearStartDate)))
			.map(EnrollmentLookupService::ref);
	}

	@Override
	public Optional<ClassRecordRef> byStudentAndYear(Long studentId, Long yearId) {
		return enrollments.findByStudentIdAndAcademicYearId(studentId, yearId).map(EnrollmentLookupService::ref);
	}

	private static ClassRecordRef ref(StudentEnrollment record) {
		return new ClassRecordRef(record.getId(), record.getStudentId(), record.getAcademicYearId(),
				record.getStandardId(), record.getCategoryId());
	}

}
