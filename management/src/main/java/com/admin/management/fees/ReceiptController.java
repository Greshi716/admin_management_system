package com.admin.management.fees;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Save a receipt and read one back. There is deliberately no endpoint that edits, cancels or deletes a receipt
 * (FR-009); a wrong receipt is corrected by the developer runbook (FR-022).
 */
@RestController
@RequestMapping("/api/receipts")
class ReceiptController {

	private final ReceiptService service;

	private final ReceiptMarkService marks;

	ReceiptController(ReceiptService service, ReceiptMarkService marks) {
		this.service = service;
		this.marks = marks;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ReceiptDto create(@RequestBody ReceiptInput input) {
		return service.create(input);
	}

	@GetMapping("/{serialNo}")
	ReceiptDto get(@PathVariable Long serialNo) {
		return service.get(serialNo);
	}

	/** The only change to a receipt: it is marked, never edited (FR-013). */
	@PostMapping("/{serialNo}/not-cleared")
	ReceiptDto markNotCleared(@PathVariable Long serialNo, @RequestBody NotClearedInput input) {
		return marks.markNotCleared(serialNo, input);
	}

}
