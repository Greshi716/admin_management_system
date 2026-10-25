package com.admin.management.masterdata.standard;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A class level such as Standard 1 or Standard 11 Science (FR-014). */
@Entity
@Table(name = "standards")
public class Standard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	private String stream;

	@Column(nullable = false)
	private boolean active = true;

	@Version
	private Long version;

	protected Standard() {
	}

	public Standard(String name, int displayOrder, String stream) {
		this.name = name;
		this.displayOrder = displayOrder;
		this.stream = stream;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public String getStream() {
		return stream;
	}

	public boolean isActive() {
		return active;
	}

	public Long getVersion() {
		return version;
	}

	public void change(String newName, int newOrder, String newStream) {
		this.name = newName;
		this.displayOrder = newOrder;
		this.stream = newStream;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Map<String, Object> snapshot() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", name);
		values.put("displayOrder", displayOrder);
		values.put("stream", stream);
		values.put("active", active);
		return values;
	}

}
