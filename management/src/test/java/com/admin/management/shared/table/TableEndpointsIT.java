package com.admin.management.shared.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import com.admin.management.audit.AuditEntry;
import com.admin.management.shared.excel.ExcelExporter;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

@Covers("FR-033")
@Covers("FR-034")
class TableEndpointsIT extends IntegrationTest {

	@Autowired
	TableEndpoints endpoints;

	@Autowired
	JdbcTemplate jdbc;

	TableDef<AuditEntry> def = TableDef.of(AuditEntry.class,
			List.of(ColumnDef.<AuditEntry>text("area", "Area"), ColumnDef.<AuditEntry>text("entity", "Entity"),
					ColumnDef.<AuditEntry>text("reason", "Reason").asSensitive().asHidden()));

	@BeforeAll
	void seed() {
		jdbc.update("""
				insert into audit_log (area, action, entity, reason)
				select case when i <= 12 then 'Fees' else 'Payroll' end, 'Create', 'item#' || lpad(i::text, 2, '0'),
				       '987654321098'
				from generate_series(1, 30) as i
				""");
	}

	@Test
	void searchReturnsAPageFromTheDeclaredColumnsOnly() {
		TablePage page = endpoints.search(def, new TableRequest(0, 10, "entity", 1, null,
				Map.of("area", new TableRequest.Filter("Fees", "equals"))));

		assertThat(page.total()).isEqualTo(12);
		assertThat(page.rows()).hasSize(10);
	}

	@Test
	void exportWritesAllMatchingRowsNotJustThePageAndLogsAnExportEntry() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		TableRequest request = new TableRequest(0, 10, "entity", -1, null,
				Map.of("area", new TableRequest.Filter("Fees", "equals")));

		endpoints.export("Master data", "Fees list", def, request, null, response);

		assertThat(response.getContentType()).isEqualTo(ExcelExporter.CONTENT_TYPE);
		assertThat(response.getHeader("Content-Disposition")).startsWith("attachment; filename=\"fees-list-")
			.endsWith(".xlsx\"");
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
			var sheet = workbook.getSheetAt(0);
			assertThat(sheet.getLastRowNum()).isEqualTo(12);
			assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Reason");
			assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("item#12");
			assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("********1098");
		}
		Map<String, Object> audit = jdbc.queryForMap("select * from audit_log where action = 'Export'");
		assertThat(audit.get("area")).isEqualTo("Master data");
		assertThat(audit.get("entity")).isEqualTo("Fees list");
		assertThat(audit.get("changes").toString()).contains("\"rows\": 12").contains("equals: Fees")
			.contains("entity desc");
	}

	@Test
	void failedExportLeavesNoExportEntry() {
		Integer before = jdbc.queryForObject("select count(*) from audit_log where action = 'Export'", Integer.class);
		MockHttpServletResponse response = new MockHttpServletResponse();
		TableRequest bad = new TableRequest(0, 10, "nope", 1, null, null);

		assertThatThrownBy(() -> endpoints.export("Master data", "Broken", def, bad, null, response))
			.isInstanceOf(ValidationException.class);

		Integer after = jdbc.queryForObject("select count(*) from audit_log where action = 'Export'", Integer.class);
		assertThat(after).isEqualTo(before);
		assertThat(response.getContentAsByteArray()).isEmpty();
	}

	@Test
	void sensitiveFilterValuesAreMaskedInTheExportEntry() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		TableRequest request = new TableRequest(0, 10, null, null, null,
				Map.of("reason", new TableRequest.Filter("987654321098", "equals")));

		endpoints.export("Master data", "Secret list", def, request, null, response);

		String changes = jdbc.queryForObject("select changes::text from audit_log where entity = 'Secret list'",
				String.class);
		assertThat(changes).contains("********1098").doesNotContain("987654321098");
	}

}
