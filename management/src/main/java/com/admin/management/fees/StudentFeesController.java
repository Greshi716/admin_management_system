package com.admin.management.fees;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Fees block of the student profile (FR-009). */
@RestController
@RequestMapping("/api")
class StudentFeesController {

	private final StudentFeesService service;

	StudentFeesController(StudentFeesService service) {
		this.service = service;
	}

	@GetMapping("/students/{id}/fees")
	StudentFeesDto fees(@PathVariable Long id, @RequestParam(required = false) Long yearId) {
		return service.fees(id, yearId);
	}

}
