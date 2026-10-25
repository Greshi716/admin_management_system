package com.admin.management.shared.table;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.audit.AuditEntry;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

/** Separate class because audit rows cannot be deleted, so its bulk rows would skew other tests' counts. */
@Covers("FR-033")
@Covers("FR-035")
class TableQueryStreamIT extends IntegrationTest {

	@Autowired
	TableQueryService tables;

	@Autowired
	JdbcTemplate jdbc;

	TableDef<AuditEntry> def = TableDef.of(AuditEntry.class,
			List.of(ColumnDef.<AuditEntry>text("area", "Area"), ColumnDef.<AuditEntry>text("entity", "Entity")));

	@Test
	void streamReadsEveryMatchingRowInChunksWithoutGapsOrRepeats() {
		jdbc.update("""
				insert into audit_log (area, action, entity)
				select 'Bulk', 'Import', 'bulk#' || lpad(i::text, 5, '0') from generate_series(1, 1203) as i
				""");
		TableRequest request = new TableRequest(0, 10, "entity", 1, null,
				Map.of("area", new TableRequest.Filter("Bulk", "equals")));
		List<Object> entities = new ArrayList<>();

		long read = tables.stream(def, request, null, row -> entities.add(row.get("entity")));

		assertThat(read).isEqualTo(1203);
		assertThat(entities).doesNotHaveDuplicates().isSorted();
		assertThat(entities.get(0)).isEqualTo("bulk#00001");
		assertThat(entities.get(1202)).isEqualTo("bulk#01203");
	}

	@Test
	void aRowWrittenWhileTheExportRunsDoesNotRepeatOrSkipARow() {
		jdbc.update("""
				insert into audit_log (area, action, entity)
				select 'Race', 'Import', 'race#' || lpad(i::text, 5, '0') from generate_series(1, 1203) as i
				""");
		TableRequest request = new TableRequest(0, 10, "entity", 1, null,
				Map.of("area", new TableRequest.Filter("Race", "equals")));
		List<Object> entities = new ArrayList<>();

		long read = tables.stream(def, request, null, row -> {
			if (entities.isEmpty()) {
				// Committed on another connection after the first chunk; it sorts before every other row, so it
				// would shift the offset of the next chunk by one if the chunks did not share a snapshot.
				insertOnAnotherConnection();
			}
			entities.add(row.get("entity"));
		});

		assertThat(read).isEqualTo(1203);
		assertThat(entities).doesNotHaveDuplicates().isSorted().doesNotContain("race#00000");
	}

	/** A new thread has no transaction, so the insert is committed on its own connection while the export runs. */
	private void insertOnAnotherConnection() {
		Thread writer = new Thread(() -> jdbc
			.update("insert into audit_log (area, action, entity) values ('Race', 'Import', 'race#00000')"));
		writer.start();
		try {
			writer.join();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
