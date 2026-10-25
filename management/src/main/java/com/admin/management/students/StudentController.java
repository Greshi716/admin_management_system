package com.admin.management.students;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Register, edit, mark as left, class record changes, pick-lists and name matches. The request bodies are never
 * logged: they hold Aadhaar and religion/caste (constitution VI).
 */
@RestController
@RequestMapping("/api")
class StudentController {

	private final StudentService service;

	StudentController(StudentService service) {
		this.service = service;
	}

	@PostMapping("/students")
	ResponseEntity<StudentDto> register(@RequestBody StudentInput body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.register(body));
	}

	@PutMapping("/students/{id}")
	StudentDto edit(@PathVariable Long id, @RequestBody StudentInput body) {
		return service.edit(id, body);
	}

	@PutMapping("/students/{id}/left")
	StudentDto markLeft(@PathVariable Long id, @RequestBody VersionInput body) {
		return service.markLeft(id, body.version());
	}

	@PutMapping("/students/{id}/enrollments/{yearId}")
	EnrollmentDto changeEnrollment(@PathVariable Long id, @PathVariable Long yearId,
			@RequestBody EnrollmentInput body) {
		return service.changeEnrollment(id, yearId, body);
	}

	record VersionInput(Long version) {
	}

	@GetMapping("/students/name-matches")
	List<NameMatch> nameMatches(@RequestParam String surname, @RequestParam String firstName,
			@RequestParam(required = false) String middleName, @RequestParam(required = false) Long excludeId) {
		return service.nameMatches(surname, firstName, middleName, excludeId);
	}

	/** The only endpoint that returns the full Aadhaar and religion/caste; the answer must not be cached. */
	@GetMapping("/students/{id}/edit")
	ResponseEntity<StudentInput> editForm(@PathVariable Long id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.editForm(id));
	}

	@GetMapping("/student-pick-lists")
	PickListsDto pickLists(@RequestParam(required = false) Long yearId, @RequestParam(required = false) Long studentId) {
		return service.pickLists(yearId, studentId);
	}

}
