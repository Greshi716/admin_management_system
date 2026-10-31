package com.admin.management.yearchange;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for year change promotion status, screen data, and promote execution (FR-006 to FR-012, FR-014, FR-016).
 */
@RestController
@RequestMapping("/api/year-change")
public class PromotionController {

	private final PromotionStatusService statusService;

	private final PromotionService promotionService;

	public PromotionController(PromotionStatusService statusService, PromotionService promotionService) {
		this.statusService = statusService;
		this.promotionService = promotionService;
	}

	@GetMapping("/status")
	public YearChangeStatusDto getStatus() {
		return statusService.getStatus();
	}

	@GetMapping("/standards/{standardId}/students")
	public PromoteScreenDto getPromoteScreen(@PathVariable("standardId") long standardId) {
		return promotionService.getPromoteScreen(standardId);
	}

	@PostMapping("/standards/{standardId}/promote")
	public PromoteResult promote(@PathVariable("standardId") long standardId, @RequestBody PromoteInput input) {
		return promotionService.promote(standardId, input);
	}

}
