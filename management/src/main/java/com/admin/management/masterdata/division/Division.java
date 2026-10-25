package com.admin.management.masterdata.division;

import java.util.LinkedHashMap;
import java.util.Map;

import com.admin.management.masterdata.academicyear.AcademicYear;
import com.admin.management.masterdata.standard.Standard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A division (class section) of a standard in one academic year (FR-016, FR-017). */
@Entity
@Table(name = "divisions")
public class Division {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false)
	private AcademicYear academicYear;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "standard_id", nullable = false)
	private Standard standard;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private boolean active = true;

	@Version
	private Long version;

	protected Division() {
	}

	public Division(AcademicYear academicYear, Standard standard, String name) {
		this.academicYear = academicYear;
		this.standard = standard;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public AcademicYear getAcademicYear() {
		return academicYear;
	}

	public Standard getStandard() {
		return standard;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public Long getVersion() {
		return version;
	}

	public void change(AcademicYear newYear, Standard newStandard, String newName) {
		this.academicYear = newYear;
		this.standard = newStandard;
		this.name = newName;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Map<String, Object> snapshot() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("academicYear", academicYear.getName());
		values.put("standard", standard.getName());
		values.put("name", name);
		values.put("active", active);
		return values;
	}

}
