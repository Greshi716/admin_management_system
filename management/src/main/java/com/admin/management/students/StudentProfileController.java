package com.admin.management.students;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The student profile and its History block. Aadhaar and religion/caste are masked in both (FR-010). */
@RestController
@RequestMapping("/api/students/{id}")
class StudentProfileController {

	private final StudentService service;

	StudentProfileController(StudentService service) {
		this.service = service;
	}

	@GetMapping
	StudentDto profile(@PathVariable Long id) {
		return service.get(id);
	}

	@GetMapping("/history")
	StudentHistoryDto history(@PathVariable Long id) {
		return service.history(id);
	}

}
