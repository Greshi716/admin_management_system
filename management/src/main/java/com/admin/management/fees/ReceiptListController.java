package com.admin.management.fees;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableQueryService;
import com.admin.management.shared.table.TableRequest;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The receipt register, its Excel export and the day-wise totals of the same filter (FR-019, FR-020, FR-021). The
 * export is audited in area Fees, once, after the file has been produced.
 */
@RestController
@RequestMapping("/api/receipts")
class ReceiptListController {

	static final String LIST_NAME = "Receipts";

	private final TableEndpoints tables;

	private final TableQueryService queries;

	ReceiptListController(TableEndpoints tables, TableQueryService queries) {
		this.tables = tables;
		this.queries = queries;
	}

	@PostMapping("/search")
	TablePage search(@RequestBody TableRequest request) {
		return tables.search(ReceiptTable.build(), request);
	}

	@PostMapping("/export")
	void export(@RequestBody TableRequest request, HttpServletResponse response) throws IOException {
		tables.export("Fees", LIST_NAME, ReceiptTable.build(), request, null, response, new DayTotals());
	}

	/** The same totals the export's second sheet holds, for the panel beside the list. */
	@PostMapping("/day-totals")
	List<DayTotals.Day> dayTotals(@RequestBody TableRequest request) {
		DayTotals totals = new DayTotals();
		queries.stream(ReceiptTable.build(), request, null, totals::observe);
		return totals.days();
	}

}
