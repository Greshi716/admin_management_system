package com.admin.management.masterdata.category;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A student category such as RTE or STAFF, with the flag that says whether it affects fees (FR-018, FR-019). */
@Entity
@Table(name = "student_categories")
public class StudentCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(name = "affects_fees", nullable = false)
	private boolean affectsFees;

	@Column(nullable = false)
	private boolean active = true;

	@Version
	private Long version;

	protected StudentCategory() {
	}

	public StudentCategory(String code, String name, boolean affectsFees) {
		this.code = code;
		this.name = name;
		this.affectsFees = affectsFees;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public boolean isAffectsFees() {
		return affectsFees;
	}

	public boolean isActive() {
		return active;
	}

	public Long getVersion() {
		return version;
	}

	public void change(String newCode, String newName, boolean newAffectsFees) {
		this.code = newCode;
		this.name = newName;
		this.affectsFees = newAffectsFees;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Map<String, Object> snapshot() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("code", code);
		values.put("name", name);
		values.put("affectsFees", affectsFees);
		values.put("active", active);
		return values;
	}

}
