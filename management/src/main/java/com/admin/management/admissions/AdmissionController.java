package com.admin.management.admissions;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableRequest;

/**
 * Controller for Google Form admissions endpoints (FR-028 to FR-036).
 */
@RestController
@RequestMapping("/api/admissions")
public class AdmissionController {

	private final AdmissionImportService importService;
	private final AdmissionReviewService reviewService;

	public AdmissionController(AdmissionImportService importService, AdmissionReviewService reviewService) {
		this.importService = importService;
		this.reviewService = reviewService;
	}

	@PostMapping("/upload")
	public ResponseEntity<ImportResultDto> upload(@RequestParam("file") MultipartFile file) {
		ImportResultDto result = importService.importFile(file);
		return ResponseEntity.ok(result);
	}

	@GetMapping("/pending-count")
	public ResponseEntity<Map<String, Object>> pendingCount() {
		long count = reviewService.getPendingCount();
		return ResponseEntity.ok(Map.of("pending", count));
	}

	@GetMapping
	public ResponseEntity<TablePage> list(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean duplicate,
			@RequestParam(defaultValue = "0") int first,
			@RequestParam(defaultValue = "25") int rows,
			@RequestParam(required = false) String search) {
		TablePage page = reviewService.list(status, duplicate, first, rows, search);
		return ResponseEntity.ok(page);
	}

	@PostMapping("/search")
	public ResponseEntity<TablePage> search(@RequestBody TableRequest request) {
		TablePage page = reviewService.search(request);
		return ResponseEntity.ok(page);
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdmissionDetailDto> getDetail(@PathVariable Long id) {
		AdmissionDetailDto detail = reviewService.getDetail(id);
		return ResponseEntity.ok(detail);
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<Map<String, Object>> approve(
			@PathVariable Long id,
			@RequestBody ApproveInput input) {
		Map<String, Object> result = reviewService.approve(id, input);
		return ResponseEntity.ok(result);
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<Map<String, Object>> reject(
			@PathVariable Long id,
			@RequestBody RejectInput input) {
		Map<String, Object> result = reviewService.reject(id, input);
		return ResponseEntity.ok(result);
	}

}
