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

	ReceiptController(ReceiptService service) {
		this.service = service;
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

}
