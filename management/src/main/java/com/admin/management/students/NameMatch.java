package com.admin.management.students;

/** A student with the same names as the one being registered (FR-003). */
public record NameMatch(Long id, String grNo, String name, String status) {
}
