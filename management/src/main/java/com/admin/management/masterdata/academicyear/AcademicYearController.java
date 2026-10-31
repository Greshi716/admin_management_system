package com.admin.management.masterdata.academicyear;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.masterdata.MasterDataAudit;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableRequest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** {@code /api/academic-years}. Academic years have no {@code /active} and no {@code make-current} (FR-012, FR-020). */
@RestController
@RequestMapping("/api/academic-years")
class AcademicYearController {

	private final AcademicYearService service;

	private final TableEndpoints tables;

	private final Clock clock;

	private final java.util.Optional<com.admin.management.masterdata.api.YearCopyHandler> yearCopyHandler;

	AcademicYearController(AcademicYearService service, TableEndpoints tables, Clock clock, java.util.Optional<com.admin.management.masterdata.api.YearCopyHandler> yearCopyHandler) {
		this.service = service;
		this.tables = tables;
		this.clock = clock;
		this.yearCopyHandler = yearCopyHandler;
	}

	/** Declared per request because the "Current" column depends on today's date. */
	private TableDef<AcademicYear> table() {
		LocalDate today = LocalDate.now(clock);
		return TableDef
			.of(AcademicYear.class,
					List.of(ColumnDef.<AcademicYear>number("id", "Id").asInternal(),
							ColumnDef.<AcademicYear>number("version", "Version").asInternal(),
							ColumnDef.<AcademicYear>text("name", "Name"),
							ColumnDef.<AcademicYear>date("startDate", "Start date"),
							ColumnDef.<AcademicYear>date("endDate", "End date"),
							ColumnDef.<AcademicYear>bool("current", "Current")
								.expression((root, cb) -> cb.<Boolean>selectCase()
									.when(cb.and(cb.lessThanOrEqualTo(root.<LocalDate>get("startDate"), today),
											cb.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), today)),
											true)
									.otherwise(false))))
			.sortedBy("startDate", -1);
	}

	@GetMapping
	List<AcademicYearDto> list() {
		return service.list();
	}

	@PostMapping
	ResponseEntity<?> create(@Valid @RequestBody AcademicYearDto body) {
		if (Boolean.TRUE.equals(body.copyFromLast()) && yearCopyHandler.isPresent()) {
			var result = yearCopyHandler.get().createWithCopy(body.name(), body.startDate(), body.endDate());
			return ResponseEntity.status(HttpStatus.CREATED).body(result);
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
	}

	@PostMapping("/search")
	TablePage search(@RequestBody TableRequest request) {
		return tables.search(table(), request);
	}

	@PostMapping("/export")
	void export(@RequestBody TableRequest request, HttpServletResponse response) throws IOException {
		tables.export(MasterDataAudit.AREA, "Academic years", table(), request, null, response);
	}

	@GetMapping("/{id}")
	AcademicYearDto get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/{id}")
	AcademicYearDto update(@PathVariable Long id, @Valid @RequestBody AcademicYearDto body) {
		return service.update(id, body);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
