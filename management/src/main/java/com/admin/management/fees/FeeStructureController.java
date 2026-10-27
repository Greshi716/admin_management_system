package com.admin.management.fees;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The fee structure of an academic year: read it, and add, edit and delete its rows (FR-001 to FR-003). */
@RestController
@RequestMapping("/api")
class FeeStructureController {

	private final FeeStructureService service;

	FeeStructureController(FeeStructureService service) {
		this.service = service;
	}

	@GetMapping("/fee-structures/{yearId}")
	FeeStructureDto structure(@PathVariable Long yearId) {
		return service.structure(yearId);
	}

	@GetMapping("/fee-structures/{yearId}/choices")
	FeeChoicesDto choices(@PathVariable Long yearId) {
		return service.choices(yearId);
	}

	@PostMapping("/fee-structures/{yearId}/rows")
	ResponseEntity<FeeRowDto> addRow(@PathVariable Long yearId, @RequestBody FeeRowInput body,
			@RequestParam(defaultValue = "false") boolean confirm) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.addRow(yearId, body, confirm));
	}

	@PutMapping("/fee-structures/{yearId}/rows/{rowId}")
	FeeRowDto editRow(@PathVariable Long yearId, @PathVariable Long rowId, @RequestBody FeeRowInput body,
			@RequestParam(defaultValue = "false") boolean confirm) {
		return service.editRow(yearId, rowId, body, confirm);
	}

	@DeleteMapping("/fee-structures/{yearId}/rows/{rowId}")
	ResponseEntity<Void> deleteRow(@PathVariable Long yearId, @PathVariable Long rowId,
			@RequestParam(required = false) Long version, @RequestParam(required = false) Long structureVersion,
			@RequestParam(defaultValue = "false") boolean confirm) {
		service.deleteRow(yearId, rowId, version, structureVersion, confirm);
		return ResponseEntity.noContent().build();
	}

}
