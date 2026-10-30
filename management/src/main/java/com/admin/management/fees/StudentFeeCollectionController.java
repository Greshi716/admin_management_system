package com.admin.management.fees;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the Collect fee screen reads for a student: the context and the preview of a receipt (FR-008, FR-014). */
@RestController
@RequestMapping("/api/students/{studentId}/fee-collection")
class StudentFeeCollectionController {

	private final FeeCollectionService service;

	StudentFeeCollectionController(FeeCollectionService service) {
		this.service = service;
	}

	@GetMapping
	FeeCollectionDto context(@PathVariable Long studentId) {
		return service.context(studentId);
	}

	@PostMapping("/preview")
	ReceiptPreviewDto preview(@PathVariable Long studentId, @RequestBody ReceiptInput input,
			@RequestParam(defaultValue = "false") boolean validate) {
		return service.preview(studentId, input, validate);
	}

}
