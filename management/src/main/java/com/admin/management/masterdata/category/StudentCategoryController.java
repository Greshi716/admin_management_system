package com.admin.management.masterdata.category;

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

/** {@code /api/student-categories}. */
@RestController
@RequestMapping("/api/student-categories")
class StudentCategoryController {

	private static final TableDef<StudentCategory> TABLE = TableDef
		.of(StudentCategory.class,
				List.of(ColumnDef.<StudentCategory>number("id", "Id").asInternal(),
						ColumnDef.<StudentCategory>number("version", "Version").asInternal(),
						ColumnDef.<StudentCategory>text("code", "Code"), ColumnDef.<StudentCategory>text("name", "Name"),
						ColumnDef.<StudentCategory>bool("affectsFees", "Affects fees"),
						ColumnDef.<StudentCategory>bool("active", "Active")))
		.sortedBy("id", 1);

	private final StudentCategoryService service;

	private final TableEndpoints tables;

	StudentCategoryController(StudentCategoryService service, TableEndpoints tables) {
		this.service = service;
		this.tables = tables;
	}

	@GetMapping
	List<StudentCategoryDto> listActive() {
		return service.listActive();
	}

	@PostMapping
	ResponseEntity<StudentCategoryDto> create(@Valid @RequestBody StudentCategoryDto body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
	}

	@PostMapping("/search")
	TablePage search(@RequestBody TableRequest request) {
		return tables.search(TABLE, request);
	}

	@PostMapping("/export")
	void export(@RequestBody TableRequest request, HttpServletResponse response) throws IOException {
		tables.export(MasterDataAudit.AREA, "Student categories", TABLE, request, null, response);
	}

	@PutMapping("/{id}")
	StudentCategoryDto update(@PathVariable Long id, @Valid @RequestBody StudentCategoryDto body) {
		return service.update(id, body);
	}

	@PutMapping("/{id}/active")
	StudentCategoryDto setActive(@PathVariable Long id, @Valid @RequestBody ActiveInput body) {
		return service.setActive(id, body);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
