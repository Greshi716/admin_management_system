package com.admin.management.masterdata.division;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.masterdata.ActiveInput;
import com.admin.management.masterdata.MasterDataAudit;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableRequest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** {@code /api/divisions}. The list shows the academic year and standard names through joins. */
@RestController
@RequestMapping("/api/divisions")
class DivisionController {

	private static final TableDef<Division> TABLE = TableDef
		.of(Division.class,
				List.of(ColumnDef.<Division>number("id", "Id").asInternal(),
						ColumnDef.<Division>number("version", "Version").asInternal(),
						ColumnDef.<Division>number("academicYearId", "Academic year id").path("academicYear.id").asInternal(),
						ColumnDef.<Division>number("standardId", "Standard id").path("standard.id").asInternal(),
						ColumnDef.<Division>text("academicYearName", "Academic year").path("academicYear.name"),
						ColumnDef.<Division>text("standardName", "Standard").path("standard.name"),
						ColumnDef.<Division>text("name", "Division"), ColumnDef.<Division>bool("active", "Active")))
		.sortedBy("academicYearName", -1);

	private final DivisionService service;

	private final TableEndpoints tables;

	DivisionController(DivisionService service, TableEndpoints tables) {
		this.service = service;
		this.tables = tables;
	}

	@PostMapping
	ResponseEntity<DivisionDto> create(@Valid @RequestBody DivisionDto body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
	}

	@PostMapping("/search")
	TablePage search(@RequestBody TableRequest request) {
		return tables.search(TABLE, request);
	}

	@PostMapping("/export")
	void export(@RequestBody TableRequest request, HttpServletResponse response) throws IOException {
		tables.export(MasterDataAudit.AREA, "Divisions", TABLE, request, null, response);
	}

	@PutMapping("/{id}")
	DivisionDto update(@PathVariable Long id, @Valid @RequestBody DivisionDto body) {
		return service.update(id, body);
	}

	@PutMapping("/{id}/active")
	DivisionDto setActive(@PathVariable Long id, @Valid @RequestBody ActiveInput body) {
		return service.setActive(id, body);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
