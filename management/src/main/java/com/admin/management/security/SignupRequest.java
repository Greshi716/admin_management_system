package com.admin.management.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank(message = "Enter your name.") @Size(max = 100, message = "Use at most 100 characters.") String name,
		@NotBlank(message = "Choose a username.") @Size(max = 50, message = "Use at most 50 characters.") String username,
		@NotBlank(message = "Choose a password.") String password,
		@NotBlank(message = "Enter the password again.") String confirmPassword) {

	/** Keeps the passwords out of any accidental log line. */
	@Override
	public String toString() {
		return "SignupRequest[username=" + username + "]";
	}

}
