package com.admin.management.masterdata.academicyear;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A school year such as 2026-27. It has no "current" or "active" column: the current year is the one whose dates
 * contain today (FR-012), and an academic year cannot be made inactive (FR-020).
 */
@Entity
@Table(name = "academic_years")
public class AcademicYear {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Version
	private Long version;

	protected AcademicYear() {
	}

	public AcademicYear(String name, LocalDate startDate, LocalDate endDate) {
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public Long getVersion() {
		return version;
	}

	public void change(String newName, LocalDate newStart, LocalDate newEnd) {
		this.name = newName;
		this.startDate = newStart;
		this.endDate = newEnd;
	}

	/** The values an audit entry records (dates as text). */
	public Map<String, Object> snapshot() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", name);
		values.put("startDate", startDate.toString());
		values.put("endDate", endDate.toString());
		return values;
	}

}
