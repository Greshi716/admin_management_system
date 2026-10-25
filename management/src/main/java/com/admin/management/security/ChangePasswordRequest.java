package com.admin.management.security;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank(message = "Enter your current password.") String currentPassword,
		@NotBlank(message = "Enter a new password.") String newPassword) {

	/** Keeps both passwords out of any accidental log line. */
	@Override
	public String toString() {
		return "ChangePasswordRequest[]";
	}

}
