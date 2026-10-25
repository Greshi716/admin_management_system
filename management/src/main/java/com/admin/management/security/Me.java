package com.admin.management.security;

/** The signed-in user as the web app sees it, matching {@code Me} in contracts/openapi.yaml. */
public record Me(String name, String username, String role, boolean passwordChanged, String currentAcademicYear,
		int idleTimeoutSeconds) {
}
