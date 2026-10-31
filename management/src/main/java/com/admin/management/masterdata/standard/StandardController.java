package com.admin.management.masterdata.standard;

import java.io.IOException;
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

import com.admin.management.masterdata.ActiveInput;
import com.admin.management.masterdata.MasterDataAudit;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableRequest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** {@code /api/standards}. */
@RestController
@RequestMapping("/api/standards")
class StandardController {

	private static final TableDef<Standard> TABLE = TableDef
		.of(Standard.class,
				List.of(ColumnDef.<Standard>number("id", "Id").asInternal(),
						ColumnDef.<Standard>number("version", "Version").asInternal(),
						ColumnDef.<Standard>text("name", "Name"),
						ColumnDef.<Standard>number("displayOrder", "Display order"),
						ColumnDef.<Standard>text("stream", "Stream"),
						ColumnDef.<Standard>bool("usesStream", "Uses stream"),
						ColumnDef.<Standard>bool("active", "Active")))
		.sortedBy("displayOrder", 1);

	private final StandardService service;

	private final TableEndpoints tables;

	StandardController(StandardService service, TableEndpoints tables) {
		this.service = service;
		this.tables = tables;
	}

	@GetMapping
	List<StandardDto> listActive() {
		return service.listActive();
	}

	@PostMapping
	ResponseEntity<StandardDto> create(@Valid @RequestBody StandardDto body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
	}

	@PostMapping("/search")
	TablePage search(@RequestBody TableRequest request) {
		return tables.search(TABLE, request);
	}

	@PostMapping("/export")
	void export(@RequestBody TableRequest request, HttpServletResponse response) throws IOException {
		tables.export(MasterDataAudit.AREA, "Standards", TABLE, request, null, response);
	}

	@PutMapping("/{id}")
	StandardDto update(@PathVariable Long id, @Valid @RequestBody StandardDto body) {
		return service.update(id, body);
	}

	@PutMapping("/{id}/active")
	StandardDto setActive(@PathVariable Long id, @Valid @RequestBody ActiveInput body) {
		return service.setActive(id, body);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
