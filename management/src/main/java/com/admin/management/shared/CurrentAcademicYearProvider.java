package com.admin.management.shared;

/**
 * Supplies the name of the academic year that contains today's date. Defined here so sign-in does not depend
 * on the master-data module; the academic-years module provides the real one (FR-012, FR-028).
 */
public interface CurrentAcademicYearProvider {

	/** The current academic year's name, or null when today falls in no academic year. */
	String currentName();

}
