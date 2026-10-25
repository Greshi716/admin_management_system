package com.admin.management.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.masterdata.standard.Standard;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableRequest;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

/**
 * The reusable table over a list that was declared here from nothing but a column list (the standards table with a
 * test-only {@link TableDef}), so it shows a new list needs no query code of its own (FR-034). The database holds
 * only synthetic names.
 */
@Covers("FR-029")
@Covers("FR-030")
@Covers("FR-031")
@Covers("FR-032")
@Covers("FR-034")
class TableQueryIT extends IntegrationTest {

	@Autowired
	TableEndpoints tables;

	@Autowired
	JdbcTemplate jdbc;

	/** The whole declaration of a list: its columns and the order used when none is asked for. */
	static final TableDef<Standard> LIST = TableDef
		.of(Standard.class,
				List.of(ColumnDef.<Standard>number("id", "Id").asInternal(), ColumnDef.<Standard>text("name", "Name"),
						ColumnDef.<Standard>number("displayOrder", "Order"), ColumnDef.<Standard>text("stream", "Stream"),
						ColumnDef.<Standard>bool("active", "Active")))
		.sortedBy("displayOrder", 1);

	@BeforeEach
	void thirtyStandards() {
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		for (int i = 1; i <= 30; i++) {
			String stream = i % 10 == 0 ? "Science" : null;
			jdbc.update("insert into standards (name, display_order, stream, active) values (?, ?, ?, ?)",
					"Std " + String.format("%02d", i), i, stream, i % 2 == 1);
		}
	}

	@Test
	void aPageHoldsOnlyItsRowsAndTheTotalCountsEveryMatch() {
		TablePage page = tables.search(LIST, new TableRequest(10, 10, null, null, null, null));

		assertThat(page.total()).isEqualTo(30);
		assertThat(page.rows()).hasSize(10);
		assertThat(names(page)).startsWith("Std 11").endsWith("Std 20");
	}

	@Test
	void searchLooksInEveryVisibleTextColumn() {
		assertThat(tables.search(LIST, search("std 07")).total()).isEqualTo(1);
		assertThat(tables.search(LIST, search("science")).total()).isEqualTo(3); // found in the Stream column
		assertThat(tables.search(LIST, search("no such standard")).total()).isZero();
	}

	@Test
	void columnFiltersCombineWithTheSearchByAnd() {
		TableRequest request = new TableRequest(0, 100, null, null, "std 1",
				Map.of("active", new TableRequest.Filter(true, "boolean")));

		TablePage page = tables.search(LIST, request);

		assertThat(names(page)).containsExactly("Std 11", "Std 13", "Std 15", "Std 17", "Std 19");
	}

	@Test
	void sortingAppliesToTheWholeListNotJustThePage() {
		TablePage firstDescending = tables.search(LIST, new TableRequest(0, 10, "name", -1, null, null));
		TablePage lastAscending = tables.search(LIST, new TableRequest(20, 10, "name", 1, null, null));

		assertThat(names(firstDescending).get(0)).isEqualTo("Std 30");
		assertThat(names(lastAscending).get(9)).isEqualTo("Std 30");
	}

	@Test
	void aListNeedsOnlyItsColumnDeclarationsToGetPagingSearchFiltersAndSort() {
		TableDef<Standard> minimal = TableDef.of(Standard.class, List.of(ColumnDef.<Standard>text("name", "Name")));

		TablePage page = tables.search(minimal, new TableRequest(0, 10, "name", -1, "std 2",
				Map.of("name", new TableRequest.Filter("9", "contains"))));

		assertThat(names(page)).containsExactly("Std 29");
		assertThat(page.total()).isEqualTo(1);
	}

	@Test
	void textWithLikeSymbolsQuotesAndGujaratiIsMatchedAsPlainText() {
		jdbc.update("insert into standards (name, display_order) values (?, 101)", "100% pass");
		jdbc.update("insert into standards (name, display_order) values (?, 102)", "under_score");
		jdbc.update("insert into standards (name, display_order) values (?, 103)", "O'Brien \"quoted\"");
		jdbc.update("insert into standards (name, display_order) values (?, 104)", "back\\slash");
		jdbc.update("insert into standards (name, display_order) values (?, 105)", "ધોરણ ૧૧ વિજ્ઞાન");

		assertThat(names(tables.search(LIST, search("%")))).containsExactly("100% pass");
		assertThat(names(tables.search(LIST, search("_")))).containsExactly("under_score");
		assertThat(names(tables.search(LIST, search("o'brien")))).containsExactly("O'Brien \"quoted\"");
		assertThat(names(tables.search(LIST, search("\"quoted\"")))).containsExactly("O'Brien \"quoted\"");
		assertThat(names(tables.search(LIST, search("\\")))).containsExactly("back\\slash");
		assertThat(names(tables.search(LIST, search("ધોરણ")))).containsExactly("ધોરણ ૧૧ વિજ્ઞાન");
		assertThat(names(tables.search(LIST, search("1%")))).isEmpty(); // % is not a wildcard
		assertThat(names(tables.search(LIST, search("s_d 01")))).isEmpty(); // _ is not a wildcard
	}

	private static TableRequest search(String text) {
		return new TableRequest(0, 100, null, null, text, null);
	}

	private static List<String> names(TablePage page) {
		return page.rows().stream().map(row -> (String) row.get("name")).toList();
	}

}
