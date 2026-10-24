package com.admin.management.shared.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.audit.AuditEntry;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

/**
 * Uses the audit log as the data source because it is the first mapped table. Join-path columns are covered
 * with the divisions list once that entity exists.
 */
@Covers("FR-029")
@Covers("FR-030")
@Covers("FR-031")
@Covers("FR-032")
@Covers("FR-035")
class TableQueryServiceIT extends IntegrationTest {

	@Autowired
	TableQueryService tables;

	@Autowired
	JdbcTemplate jdbc;

	TableDef<AuditEntry> def = TableDef.of(AuditEntry.class, List.of(
			ColumnDef.<AuditEntry>text("area", "Area"),
			ColumnDef.<AuditEntry>text("action", "Action"),
			ColumnDef.<AuditEntry>text("entity", "Entity"),
			ColumnDef.<AuditEntry>text("reason", "Reason").asSensitive().searchable(false),
			ColumnDef.<AuditEntry>date("at", "When"),
			ColumnDef.<AuditEntry>number("userId", "User").asHidden(),
			ColumnDef.<AuditEntry>bool("isSecurity", "Security")
				.expression((root, cb) -> cb.<Boolean>selectCase()
					.when(cb.equal(root.get("area"), "Security"), true)
					.otherwise(false))));

	@BeforeAll
	void seed() {
		Long adminId = jdbc.queryForObject("select id from users where username = 'testadmin'", Long.class);
		// 60 rows: every third is Security; at = 2026-01-01 12:00 India time plus i days; the first 5 have a user.
		jdbc.update("""
				insert into audit_log (at, user_id, area, action, entity, reason)
				select timestamptz '2026-01-01 12:00+05:30' + i * interval '1 day',
				       case when i <= 5 then ? end,
				       case when i % 3 = 0 then 'Security' else 'Master data' end,
				       'Create', 'row#' || lpad(i::text, 3, '0'), '123456789012'
				from generate_series(1, 60) as i
				""", adminId);
	}

	@Test
	void returnsOnePageAndTheTotal() {
		TablePage page = tables.page(def, new TableRequest(10, 10, "entity", 1, null, null));

		assertThat(page.total()).isEqualTo(60);
		assertThat(page.rows()).hasSize(10);
		assertThat(page.rows().get(0)).containsEntry("entity", "row#011");
		assertThat(page.rows().get(9)).containsEntry("entity", "row#020");
	}

	@Test
	void defaultsToFirstPageOf25() {
		TablePage page = tables.page(def, TableRequest.firstPage());

		assertThat(page.rows()).hasSize(25);
		assertThat(page.total()).isEqualTo(60);
	}

	@Test
	void sortsTheWholeResultNotJustThePage() {
		TablePage descending = tables.page(def, new TableRequest(0, 10, "entity", -1, null, null));

		assertThat(descending.rows().get(0)).containsEntry("entity", "row#060");
		TablePage lastPage = tables.page(def, new TableRequest(50, 10, "entity", 1, null, null));
		assertThat(lastPage.rows().get(9)).containsEntry("entity", "row#060");
	}

	@Test
	void searchMatchesAnySearchableColumnIgnoringCase() {
		assertThat(tables.page(def, search("ROW#05")).total()).isEqualTo(10);
		assertThat(tables.page(def, search("security")).total()).isEqualTo(20);
		assertThat(tables.page(def, search("nothing like this")).total()).isZero();
	}

	@Test
	void searchAndColumnFiltersCombineSoARowMustSatisfyAll() {
		TableRequest request = new TableRequest(0, 100, null, null, "row#0",
				Map.of("area", new TableRequest.Filter("Security", "equals")));

		assertThat(tables.page(def, request).total()).isEqualTo(20);
		TableRequest narrower = new TableRequest(0, 100, null, null, "row#05",
				Map.of("area", new TableRequest.Filter("Security", "equals")));
		assertThat(tables.page(def, narrower).total()).isEqualTo(3);
	}

	@Test
	void textFiltersSupportContainsStartsWithAndEquals() {
		assertThat(filtered("entity", "00", "contains").total()).isEqualTo(9);
		assertThat(filtered("entity", "row#00", "startsWith").total()).isEqualTo(9);
		assertThat(filtered("area", "security", "contains").total()).isEqualTo(20);
		assertThat(filtered("area", "Security", "equals").total()).isEqualTo(20);
		assertThat(filtered("area", "security", "equals").total()).isZero();
	}

	@Test
	void likeCharactersInTheInputMatchThemselves() {
		assertThat(filtered("entity", "%", "contains").total()).isZero();
		assertThat(filtered("entity", "_", "contains").total()).isZero();
		assertThat(tables.page(def, search("w#0_1")).total()).isZero();
	}

	@Test
	void booleanFilterWorksOnADerivedColumn() {
		assertThat(filtered("isSecurity", "true", "boolean").total()).isEqualTo(20);
		assertThat(filtered("isSecurity", false, "boolean").total()).isEqualTo(40);
		TablePage page = tables.page(def, new TableRequest(0, 10, "isSecurity", -1, null, null));
		assertThat(page.rows().get(0)).containsEntry("isSecurity", true);
	}

	@Test
	void numberEqualsConvertsToTheColumnType() {
		Long adminId = jdbc.queryForObject("select id from users where username = 'testadmin'", Long.class);

		assertThat(filtered("userId", adminId.toString(), "equals").total()).isEqualTo(5);
		assertThatThrownBy(() -> filtered("userId", "abc", "equals")).isInstanceOf(ValidationException.class);
	}

	@Test
	void dateRangeIsInclusiveOnBothSchoolDays() {
		assertThat(filtered("at", List.of("2026-01-10", "2026-01-12"), "dateRange").total()).isEqualTo(3);
		assertThat(filtered("at", List.of("2026-01-10", ""), "dateRange").total()).isEqualTo(52);
		assertThat(filtered("at", Map.of("to", "2026-01-02"), "dateRange").total()).isEqualTo(1);
		assertThat(filtered("at", "2026-01-10", "equals").total()).isEqualTo(1);
		assertThatThrownBy(() -> filtered("at", List.of("2026-02-01", "2026-01-01"), "dateRange"))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void sensitiveColumnsAreMaskedInRows() {
		TablePage page = tables.page(def, TableRequest.firstPage());

		assertThat(page.rows().get(0)).containsEntry("reason", "********9012");
	}

	@Test
	void scopeIsAddedToEveryQuery() {
		TableQueryService.Scope<AuditEntry> securityOnly = (root, cb) -> cb.equal(root.get("area"), "Security");

		TablePage page = tables.page(def, search("row#"), securityOnly);

		assertThat(page.total()).isEqualTo(20);
	}

	@Test
	void rejectsUnknownColumnsAndBadPaging() {
		assertThatThrownBy(() -> tables.page(def, new TableRequest(0, 25, "nope", 1, null, null)))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> filtered("nope", "x", "contains")).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> filtered("area", "x", "fuzzy")).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> tables.page(def, new TableRequest(0, 30, null, null, null, null)))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> tables.page(def, new TableRequest(-1, 25, null, null, null, null)))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> tables.page(def, new TableRequest(0, 25, "area", 2, null, null)))
			.isInstanceOf(ValidationException.class);
	}

	private TablePage filtered(String field, Object value, String matchMode) {
		return tables.page(def, new TableRequest(0, 100, null, null, null,
				Map.of(field, new TableRequest.Filter(value, matchMode))));
	}

	private TableRequest search(String text) {
		return new TableRequest(0, 100, null, null, text, null);
	}

}
