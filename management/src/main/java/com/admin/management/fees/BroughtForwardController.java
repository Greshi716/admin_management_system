package com.admin.management.fees;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Enter, change or clear the amount a student brings forward from earlier years (FR-007). */
@RestController
@RequestMapping("/api")
class BroughtForwardController {

	private final BroughtForwardService service;

	BroughtForwardController(BroughtForwardService service) {
		this.service = service;
	}

	@PutMapping("/students/{id}/brought-forward")
	StudentFeesDto.BroughtForwardDto save(@PathVariable Long id, @RequestBody BroughtForwardInput body) {
		return service.save(id, body);
	}

}
