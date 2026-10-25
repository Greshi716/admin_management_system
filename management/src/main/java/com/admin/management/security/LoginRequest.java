package com.admin.management.security;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank(message = "Enter your username.") String username,
		@NotBlank(message = "Enter your password.") String password) {

	/** Keeps the password out of any accidental log line. */
	@Override
	public String toString() {
		return "LoginRequest[username=" + username + "]";
	}

}
