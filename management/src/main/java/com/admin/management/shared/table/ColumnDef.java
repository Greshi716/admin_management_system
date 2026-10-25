package com.admin.management.shared.table;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

/**
 * One column of a list, declared once. The same declaration drives what is selected, searched, filtered,
 * sorted and exported, so a new list only declares its columns (FR-034).
 *
 * <p>
 * The column's value comes from an {@link ExpressionFactory}: a plain attribute, a join path such as
 * {@code academicYear.name}, or a derived expression such as the academic year {@code current} flag. Derived
 * expressions that depend on today's date must bind a {@code LocalDate} taken from the injected
 * {@code Clock}, never a SQL date function, so the database time zone and the test clock cannot disagree.
 *
 * @param field the key in the request and in each row of the response
 * @param header the heading shown on screen and in the Excel file
 * @param searchable whether the global search box looks in this column (visible text columns)
 * @param sensitive whether the value is masked on screen and in the export (FR-033)
 * @param hidden whether the column is hidden on screen; hidden columns are still exported
 * @param internal whether the column only carries data the screen needs (an id or version): never shown, never
 *            searched and not exported
 */
public record ColumnDef<T>(String field, String header, ColumnType type, boolean searchable, boolean sensitive,
		boolean hidden, boolean internal, ExpressionFactory<T> expression) {

	public enum ColumnType {

		TEXT, NUMBER, DATE, BOOLEAN

	}

	@FunctionalInterface
	public interface ExpressionFactory<T> {

		Expression<?> build(Root<T> root, CriteriaBuilder cb);

	}

	public static <T> ColumnDef<T> text(String field, String header) {
		return new ColumnDef<>(field, header, ColumnType.TEXT, true, false, false, false, attribute(field));
	}

	public static <T> ColumnDef<T> number(String field, String header) {
		return new ColumnDef<>(field, header, ColumnType.NUMBER, false, false, false, false, attribute(field));
	}

	public static <T> ColumnDef<T> date(String field, String header) {
		return new ColumnDef<>(field, header, ColumnType.DATE, false, false, false, false, attribute(field));
	}

	public static <T> ColumnDef<T> bool(String field, String header) {
		return new ColumnDef<>(field, header, ColumnType.BOOLEAN, false, false, false, false, attribute(field));
	}

	/** Reads the value from another attribute path, for example a joined display column. */
	public ColumnDef<T> path(String attributePath) {
		return new ColumnDef<>(field, header, type, searchable, sensitive, hidden, internal, attribute(attributePath));
	}

	/** Reads the value from a derived expression. */
	public ColumnDef<T> expression(ExpressionFactory<T> factory) {
		return new ColumnDef<>(field, header, type, searchable, sensitive, hidden, internal, factory);
	}

	public ColumnDef<T> searchable(boolean value) {
		return new ColumnDef<>(field, header, type, value, sensitive, hidden, internal, expression);
	}

	/** Marks a column that only carries an id or version for the screen: not shown, not searched, not exported. */
	public ColumnDef<T> asInternal() {
		return new ColumnDef<>(field, header, type, false, sensitive, true, true, expression);
	}

	public ColumnDef<T> asSensitive() {
		return new ColumnDef<>(field, header, type, searchable, true, hidden, internal, expression);
	}

	public ColumnDef<T> asHidden() {
		return new ColumnDef<>(field, header, type, searchable, sensitive, true, internal, expression);
	}

	private static <T> ExpressionFactory<T> attribute(String attributePath) {
		return (root, cb) -> {
			Path<?> path = root;
			for (String part : attributePath.split("\\.")) {
				path = path.get(part);
			}
			return path;
		};
	}

}
