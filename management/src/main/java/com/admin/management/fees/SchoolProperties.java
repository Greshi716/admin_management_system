package com.admin.management.fees;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * The school's name, address and phone for the receipt header (FR-017). The name is required: the application does
 * not start without it, so a receipt is never printed with an empty header.
 */
@Component
@Validated
@ConfigurationProperties("campusflow.school")
public class SchoolProperties {

	@NotBlank(message = "campusflow.school.name must be set: it is printed on every receipt")
	private String name;

	private String address = "";

	private String phone = "";

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address == null ? "" : address;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone == null ? "" : phone;
	}

}
