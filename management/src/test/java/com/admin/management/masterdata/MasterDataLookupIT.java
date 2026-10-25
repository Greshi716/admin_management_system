package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.PickLists;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

/** The public master data API that the students module uses (research R7). */
@Covers("FR-006")
@Covers("FR-017")
class MasterDataLookupIT extends IntegrationTest {

	@Autowired
	MasterDataLookup lookup;

	@Autowired
	JdbcTemplate jdbc;

	long year1;

	long year2;

	@BeforeEach
	void reset() {
		clock.reset();
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		jdbc.update("delete from academic_years");
		jdbc.update("update student_categories set active = true");
		year1 = insertYear("2026-27", "2026-04-01", "2027-03-31");
		year2 = insertYear("2028-29", "2028-04-01", "2029-03-31");
	}

	@Test
	void currentYearOnTheFirstDayTheLastDayAndInTheGapBetweenYears() {
		clock.setDate(LocalDate.of(2026, 4, 1));
		assertThat(lookup.currentYear()).hasValueSatisfying(year -> {
			assertThat(year.id()).isEqualTo(year1);
			assertThat(year.name()).isEqualTo("2026-27");
			assertThat(year.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
		});

		clock.setDate(LocalDate.of(2027, 3, 31));
		assertThat(lookup.currentYear()).hasValueSatisfying(year -> assertThat(year.id()).isEqualTo(year1));

		clock.setDate(LocalDate.of(2027, 4, 1));
		assertThat(lookup.currentYear()).isEmpty();

		clock.setDate(LocalDate.of(2028, 4, 1));
		assertThat(lookup.currentYear()).hasValueSatisfying(year -> assertThat(year.id()).isEqualTo(year2));

		clock.setDate(LocalDate.of(2026, 3, 31));
		assertThat(lookup.currentYear()).isEmpty();
	}

	@Test
	void yearStartDateById() {
		assertThat(lookup.yearStartDate(year2)).hasValue(LocalDate.of(2028, 4, 1));
		assertThat(lookup.yearStartDate(-1L)).isEmpty();
		assertThat(lookup.yearStartDate(null)).isEmpty();
	}

	@Test
	void divisionOfAnotherYearOrAnotherStandardIsRefused() {
		long std1 = insertStandard("Standard 1", 1);
		long std2 = insertStandard("Standard 2", 2);
		long a = insertDivision(year1, std1, "A");
		long otherYear = insertDivision(year2, std1, "A");

		assertThat(lookup.divisionBelongsTo(a, year1, std1)).isTrue();
		assertThat(lookup.divisionBelongsTo(a, year2, std1)).isFalse();
		assertThat(lookup.divisionBelongsTo(a, year1, std2)).isFalse();
		assertThat(lookup.divisionBelongsTo(otherYear, year1, std1)).isFalse();
		assertThat(lookup.divisionBelongsTo(-1L, year1, std1)).isFalse();
		assertThat(lookup.divisionBelongsTo(null, year1, std1)).isFalse();
	}

	@Test
	void inactiveChoicesAreRefusedForNewRecordsButStillResolveForExistingOnes() {
		long std = insertStandard("Standard 1", 1);
		long div = insertDivision(year1, std, "A");
		long category = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		assertThat(lookup.isStandardActive(std)).isTrue();
		assertThat(lookup.isDivisionActive(div)).isTrue();
		assertThat(lookup.isCategoryActive(category)).isTrue();

		jdbc.update("update standards set active = false where id = ?", std);
		jdbc.update("update divisions set active = false where id = ?", div);
		jdbc.update("update student_categories set active = false where id = ?", category);

		assertThat(lookup.isStandardActive(std)).isFalse();
		assertThat(lookup.isDivisionActive(div)).isFalse();
		assertThat(lookup.isCategoryActive(category)).isFalse();
		assertThat(lookup.isStandardActive(-1L)).isFalse();
		assertThat(lookup.isDivisionActive(null)).isFalse();
		// an existing record that holds the inactive values can still be shown
		assertThat(lookup.standardNames()).containsEntry(std, "Standard 1");
		assertThat(lookup.divisionNames()).containsEntry(div, "A");
		assertThat(lookup.categoryNames()).containsKey(category);
		assertThat(lookup.divisionBelongsTo(div, year1, std)).isTrue();
	}

	@Test
	void namesAndDisplayOrdersReflectARenameAtOnce() {
		long std = insertStandard("Standard 1", 1);
		long div = insertDivision(year1, std, "A");
		assertThat(lookup.standardNames()).containsEntry(std, "Standard 1");
		assertThat(lookup.standardDisplayOrders()).containsEntry(std, 1);
		assertThat(lookup.yearNames()).containsEntry(year1, "2026-27");

		jdbc.update("update standards set name = 'Std One', display_order = 5 where id = ?", std);
		jdbc.update("update divisions set name = 'B' where id = ?", div);
		jdbc.update("update academic_years set name = 'Renamed' where id = ?", year1);

		assertThat(lookup.standardNames()).containsEntry(std, "Std One");
		assertThat(lookup.standardDisplayOrders()).containsEntry(std, 5);
		assertThat(lookup.divisionNames()).containsEntry(div, "B");
		assertThat(lookup.yearNames()).containsEntry(year1, "Renamed");
	}

	@Test
	void pickListsHaveActiveChoicesInOrderAndKeepAnExistingInactiveValue() {
		long std2 = insertStandard("Standard 2", 2);
		long std1 = insertStandard("Standard 1", 1);
		long retired = insertStandard("Old standard", 3);
		long a1 = insertDivision(year1, std1, "A");
		long b1 = insertDivision(year1, std1, "B");
		long a2 = insertDivision(year1, std2, "A");
		long oldDiv = insertDivision(year1, retired, "A");
		insertDivision(year2, std1, "A");
		jdbc.update("update standards set active = false where id = ?", retired);
		jdbc.update("update divisions set active = false where id = ?", oldDiv);
		long hidden = jdbc.queryForObject("select max(id) from student_categories", Long.class);
		jdbc.update("update student_categories set active = false where id = ?", hidden);

		PickLists plain = lookup.pickLists(year1);
		assertThat(plain.standards()).extracting(MasterDataLookup.StandardOption::id).containsExactly(std1, std2);
		assertThat(plain.divisions()).extracting(MasterDataLookup.DivisionOption::id).containsExactly(a1, b1, a2);
		assertThat(plain.categories()).extracting(MasterDataLookup.CategoryOption::id).doesNotContain(hidden);
		assertThat(plain.categories()).hasSize(6);

		PickLists kept = lookup.pickLists(year1, retired, oldDiv, hidden);
		assertThat(kept.standards()).extracting(MasterDataLookup.StandardOption::id).containsExactly(std1, std2, retired);
		assertThat(kept.divisions()).extracting(MasterDataLookup.DivisionOption::id).containsExactly(a1, b1, a2, oldDiv);
		assertThat(kept.categories()).extracting(MasterDataLookup.CategoryOption::id).contains(hidden);
		assertThat(kept.standards().get(2).active()).isFalse();

		assertThat(lookup.pickLists(year2).divisions()).hasSize(1);
	}

	@Test
	void pickListsForAnUnknownYearAreRefused() {
		assertThatThrownBy(() -> lookup.pickLists(-1L)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> lookup.pickLists(null)).isInstanceOf(NotFoundException.class);
	}

	private long insertYear(String name, String start, String end) {
		return jdbc.queryForObject("""
				insert into academic_years (name, start_date, end_date) values (?, ?::date, ?::date) returning id
				""", Long.class, name, start, end);
	}

	private long insertStandard(String name, int order) {
		return jdbc.queryForObject("insert into standards (name, display_order) values (?, ?) returning id", Long.class,
				name, order);
	}

	private long insertDivision(long year, long standard, String name) {
		return jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, ?) returning id", Long.class,
				year, standard, name);
	}

}
