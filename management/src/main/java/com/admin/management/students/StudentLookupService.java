package com.admin.management.students;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.students.api.StudentLookup;

/** Implements the public student lookup over this module's repository. */
@Service
@Transactional(readOnly = true)
class StudentLookupService implements StudentLookup {

	private final StudentRepository students;

	StudentLookupService(StudentRepository students) {
		this.students = students;
	}

	@Override
	public boolean exists(Long studentId) {
		return studentId != null && students.findById(studentId).isPresent();
	}

	@Override
	public Optional<StudentSummary> summary(Long studentId) {
		if (studentId == null) {
			return Optional.empty();
		}
		return students.findById(studentId)
			.map((s) -> new StudentSummary(s.getId(), StudentService.fullName(s), s.getGrNo(), s.getStatus()));
	}

	@Override
	public java.util.List<DuplicateCandidate> duplicateCandidates() {
		return students.findDuplicateCandidates();
	}

}
