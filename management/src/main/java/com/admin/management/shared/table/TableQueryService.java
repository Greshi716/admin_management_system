package com.admin.management.shared.table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.admin.management.shared.masking.SensitiveMasker;
import com.admin.management.shared.table.ColumnDef.ColumnType;
import com.admin.management.shared.time.ClockConfig;
import com.admin.management.shared.web.ValidationException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

/**
 * Runs a list's {@link TableDef} against the database: search across the searchable columns (OR), column
 * filters (AND, combined with the search), sort across the whole result and paging, all in SQL (FR-029 to
 * FR-032, FR-035). Rows come back as tuples, so no entities are loaded and a chunked read for export keeps
 * memory flat.
 */
@Service
public class TableQueryService {

	/** Rows read per round trip when everything is read for an export. */
	static final int EXPORT_CHUNK = 500;

	private static final char ESCAPE = '\\';

	private final EntityManager entityManager;

	public TableQueryService(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/** Extra fixed condition for a list, for example the audit log's date range and area. */
	@FunctionalInterface
	public interface Scope<T> {

		Predicate build(Root<T> root, CriteriaBuilder cb);

	}

	@Transactional(readOnly = true)
	public <T> TablePage page(TableDef<T> def, TableRequest request) {
		return page(def, request, null);
	}

	@Transactional(readOnly = true)
	public <T> TablePage page(TableDef<T> def, TableRequest request, Scope<T> scope) {
		request.validate();
		long total = count(def, request, scope);
		return new TablePage(fetch(def, request, scope, request.first(), request.rows()), total);
	}

	/**
	 * Reads every row that matches the request (ignoring its page) and hands each to {@code sink}, in the
	 * requested order. Returns the number of rows read. It is one query read through a cursor in chunks of
	 * {@link #EXPORT_CHUNK} rows, so each row's columns are worked out once (a page query per chunk would work
	 * out the skipped rows again, which made a long list's export about ten times slower) and a row written while
	 * the export runs cannot shift a boundary and repeat or skip a row (SC-007).
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public <T> long stream(TableDef<T> def, TableRequest request, Scope<T> scope, Consumer<Map<String, Object>> sink) {
		request.validate();
		long[] read = { 0 };
		try (Stream<Tuple> tuples = query(def, request, scope).setHint("org.hibernate.fetchSize", EXPORT_CHUNK)
			.getResultStream()) {
			tuples.forEach(tuple -> {
				sink.accept(toRow(def, tuple));
				read[0]++;
			});
		}
		return read[0];
	}

	private <T> long count(TableDef<T> def, TableRequest request, Scope<T> scope) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<T> root = query.from(def.entity());
		query.select(cb.count(root));
		Predicate where = where(def, request, scope, root, cb);
		if (where != null) {
			query.where(where);
		}
		return entityManager.createQuery(query).getSingleResult();
	}

	private <T> List<Map<String, Object>> fetch(TableDef<T> def, TableRequest request, Scope<T> scope, int first,
			int max) {
		return query(def, request, scope).setFirstResult(first)
			.setMaxResults(max)
			.getResultList()
			.stream()
			.map(tuple -> toRow(def, tuple))
			.toList();
	}

	private <T> TypedQuery<Tuple> query(TableDef<T> def, TableRequest request, Scope<T> scope) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<T> root = query.from(def.entity());
		List<Selection<?>> selections = new ArrayList<>();
		for (ColumnDef<T> column : def.columns()) {
			selections.add(column.expression().build(root, cb).alias(column.field()));
		}
		query.multiselect(selections);
		Predicate where = where(def, request, scope, root, cb);
		if (where != null) {
			query.where(where);
		}
		query.orderBy(orders(def, request, root, cb));
		return entityManager.createQuery(query);
	}

	private <T> Map<String, Object> toRow(TableDef<T> def, Tuple tuple) {
		Map<String, Object> row = new LinkedHashMap<>();
		for (ColumnDef<T> column : def.columns()) {
			Object value = tuple.get(column.field());
			row.put(column.field(), column.sensitive() && value != null ? SensitiveMasker.mask(value.toString()) : value);
		}
		return row;
	}

	private <T> List<Order> orders(TableDef<T> def, TableRequest request, Root<T> root, CriteriaBuilder cb) {
		String field = request.sortField() != null ? request.sortField() : def.defaultSortField();
		int direction = request.sortField() != null ? (request.sortOrder() == null ? 1 : request.sortOrder())
				: def.defaultSortOrder();
		List<Order> orders = new ArrayList<>();
		if (field != null) {
			ColumnDef<T> column = def.column(field);
			if (column == null) {
				throw new ValidationException("sortField", "unknown column '" + field + "'");
			}
			if (column.sensitive()) {
				throw new ValidationException("sortField", "cannot sort by '" + field + "'");
			}
			List<ColumnDef.ExpressionFactory<T>> keys = column.sortKeys().isEmpty() ? List.of(column.expression())
					: column.sortKeys();
			for (ColumnDef.ExpressionFactory<T> key : keys) {
				Expression<?> expression = key.build(root, cb);
				orders.add(direction == -1 ? cb.desc(expression) : cb.asc(expression));
			}
		}
		try {
			orders.add(cb.asc(root.get("id")));
		}
		catch (IllegalArgumentException noIdAttribute) {
			// a list without an id attribute keeps only the requested order
		}
		return orders;
	}

	private <T> Predicate where(TableDef<T> def, TableRequest request, Scope<T> scope, Root<T> root,
			CriteriaBuilder cb) {
		List<Predicate> all = new ArrayList<>();
		if (scope != null) {
			all.add(scope.build(root, cb));
		}
		String search = request.globalFilter();
		if (search != null && !search.isBlank()) {
			List<Predicate> any = new ArrayList<>();
			for (ColumnDef<T> column : def.columns()) {
				if (column.searchable() && !column.sensitive()) {
					any.add(contains(column.expression().build(root, cb), search.trim(), cb));
				}
			}
			if (!any.isEmpty()) {
				all.add(cb.or(any.toArray(Predicate[]::new)));
			}
		}
		for (Map.Entry<String, TableRequest.Filter> entry : request.filters().entrySet()) {
			ColumnDef<T> column = def.column(entry.getKey());
			if (column == null) {
				throw new ValidationException("filters." + entry.getKey(), "unknown column");
			}
			if (column.sensitive()) {
				throw new ValidationException("filters." + entry.getKey(), "cannot filter by '" + entry.getKey() + "'");
			}
			Predicate predicate = filter(column, entry.getValue(), root, cb);
			if (predicate != null) {
				all.add(predicate);
			}
		}
		return all.isEmpty() ? null : cb.and(all.toArray(Predicate[]::new));
	}

	private <T> Predicate filter(ColumnDef<T> column, TableRequest.Filter filter, Root<T> root, CriteriaBuilder cb) {
		if (filter == null || filter.value() == null || filter.value().toString().isBlank()) {
			return null;
		}
		Expression<?> expression = column.expression().build(root, cb);
		String mode = filter.matchMode() == null ? "contains" : filter.matchMode();
		String where = "filters." + column.field();
		return switch (mode) {
			case "contains" -> contains(expression, filter.value().toString().trim(), cb);
			case "startsWith" -> cb.like(cb.lower(asString(expression)), escape(lower(filter.value())) + "%", ESCAPE);
			case "equals" -> equalsPredicate(column, expression, filter.value(), cb, where);
			case "boolean" -> cb.equal(expression, toBoolean(filter.value(), where));
			case "dateRange" -> dateRange(expression, filter.value(), cb, where);
			default -> throw new ValidationException(where, "unknown match mode '" + mode + "'");
		};
	}

	private Predicate contains(Expression<?> expression, String text, CriteriaBuilder cb) {
		return cb.like(cb.lower(asString(expression)), "%" + escape(text.toLowerCase(Locale.ROOT)) + "%", ESCAPE);
	}

	@SuppressWarnings("unchecked")
	private Expression<String> asString(Expression<?> expression) {
		return expression.getJavaType() == String.class ? (Expression<String>) expression
				: expression.as(String.class);
	}

	private Predicate equalsPredicate(ColumnDef<?> column, Expression<?> expression, Object value, CriteriaBuilder cb,
			String where) {
		Class<?> javaType = expression.getJavaType();
		if (column.type() == ColumnType.DATE && javaType == Instant.class) {
			LocalDate day = toDate(value, where);
			return dateRange(expression, day, day, cb);
		}
		return cb.equal(expression, convert(column.type(), javaType, value, where));
	}

	private Object convert(ColumnType type, Class<?> javaType, Object value, String where) {
		String text = value.toString().trim();
		try {
			return switch (type) {
				case TEXT -> text;
				case BOOLEAN -> toBoolean(value, where);
				case DATE -> toDate(value, where);
				case NUMBER -> toNumber(javaType, text);
			};
		}
		catch (NumberFormatException ex) {
			throw new ValidationException(where, "'" + text + "' is not a number");
		}
	}

	private Object toNumber(Class<?> javaType, String text) {
		if (javaType == Integer.class || javaType == int.class) {
			return Integer.valueOf(text);
		}
		if (javaType == Long.class || javaType == long.class) {
			return Long.valueOf(text);
		}
		if (javaType == Short.class || javaType == short.class) {
			return Short.valueOf(text);
		}
		return new BigDecimal(text);
	}

	private Boolean toBoolean(Object value, String where) {
		if (value instanceof Boolean flag) {
			return flag;
		}
		String text = value.toString().trim();
		if (text.equalsIgnoreCase("true")) {
			return Boolean.TRUE;
		}
		if (text.equalsIgnoreCase("false")) {
			return Boolean.FALSE;
		}
		throw new ValidationException(where, "must be true or false");
	}

	private LocalDate toDate(Object value, String where) {
		String text = value.toString().trim();
		try {
			return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
		}
		catch (DateTimeParseException ex) {
			throw new ValidationException(where, "'" + text + "' is not a date (use yyyy-MM-dd)");
		}
	}

	/** The value is a two-item list or a map with {@code from} and {@code to}; either end may be empty. */
	private Predicate dateRange(Expression<?> expression, Object value, CriteriaBuilder cb, String where) {
		Object from = null;
		Object to = null;
		if (value instanceof List<?> list) {
			from = list.isEmpty() ? null : list.get(0);
			to = list.size() > 1 ? list.get(1) : null;
		}
		else if (value instanceof Map<?, ?> map) {
			from = map.get("from");
			to = map.get("to");
		}
		else {
			throw new ValidationException(where, "a date range needs a from and a to value");
		}
		LocalDate start = isBlank(from) ? null : toDate(from, where);
		LocalDate end = isBlank(to) ? null : toDate(to, where);
		if (start != null && end != null && start.isAfter(end)) {
			throw new ValidationException(where, "the start date is after the end date");
		}
		return dateRange(expression, start, end, cb);
	}

	/** Inclusive on both days; a timestamp column uses the school's local days. */
	@SuppressWarnings("unchecked")
	private Predicate dateRange(Expression<?> expression, LocalDate start, LocalDate end, CriteriaBuilder cb) {
		List<Predicate> parts = new ArrayList<>();
		if (expression.getJavaType() == Instant.class) {
			Expression<Instant> instant = (Expression<Instant>) expression;
			if (start != null) {
				parts.add(cb.greaterThanOrEqualTo(instant, start.atStartOfDay(ClockConfig.SCHOOL_ZONE).toInstant()));
			}
			if (end != null) {
				parts.add(cb.lessThan(instant, end.plusDays(1).atStartOfDay(ClockConfig.SCHOOL_ZONE).toInstant()));
			}
		}
		else {
			Expression<LocalDate> date = (Expression<LocalDate>) expression;
			if (start != null) {
				parts.add(cb.greaterThanOrEqualTo(date, start));
			}
			if (end != null) {
				parts.add(cb.lessThanOrEqualTo(date, end));
			}
		}
		return parts.isEmpty() ? null : cb.and(parts.toArray(Predicate[]::new));
	}

	private static boolean isBlank(Object value) {
		return value == null || value.toString().isBlank();
	}

	private static String lower(Object value) {
		return value.toString().trim().toLowerCase(Locale.ROOT);
	}

	/** Makes {@code %}, {@code _} and the escape character match themselves in a LIKE pattern. */
	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
