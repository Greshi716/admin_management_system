package com.admin.management.students;

import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TablePage;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The Students list and its Excel export (FR-008). Aadhaar and religion/caste are sensitive columns: they are
 * masked, and the query service refuses to search, filter or sort by them (FR-010).
 */
@RestController
@RequestMapping("/api/students")
class StudentListController {

	static final String LIST_NAME = "Students";

	private final TableEndpoints tables;

	private final MasterDataLookup lookup;

	StudentListController(TableEndpoints tables, MasterDataLookup lookup) {
		this.tables = tables;
		this.lookup = lookup;
	}

	@PostMapping("/search")
	TablePage search(@RequestBody StudentListRequest request) {
		return tables.search(StudentTable.build(lookup), request.table(), StudentTable.statusScope(request.status()));
	}

	@PostMapping("/export")
	void export(@RequestBody StudentListRequest request, HttpServletResponse response) throws IOException {
		tables.export(StudentAudit.AREA, LIST_NAME, StudentTable.build(lookup), request.table(),
				StudentTable.statusScope(request.status()), response);
	}

}
