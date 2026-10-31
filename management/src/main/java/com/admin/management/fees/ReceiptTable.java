package com.admin.management.fees;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.ColumnDef.ExpressionFactory;
import com.admin.management.shared.table.TableDef;

import jakarta.persistence.criteria.Expression;

/**
 * The columns of the receipt register (FR-019), declared once for the page and the Excel export. The student, class and
 * amounts are the values stored on the receipt, so a later rename or move cannot change an old row. Status is read from
 * the marks with a correlated subquery on {@code receipt_marks} (this module's own table); a receipt with no mark is
 * Valid. There is no "Paid up to" column: a receipt carries none (FR-004).
 */
final class ReceiptTable {

	static final String STATUS_VALID = "Valid";

	static final String STATUS_NOT_CLEARED = "Cheque not cleared";

	static final String STATUS_VOIDED = "Voided by correction";

	/** The values of the status filter, as the register writes them. */
	static final List<String> STATUSES = List.of(STATUS_VALID, STATUS_NOT_CLEARED, STATUS_VOIDED);

	private static final String STATUS_SQL = "coalesce((select case m.kind when 'CHEQUE_NOT_CLEARED' then '"
			+ STATUS_NOT_CLEARED + "' else '" + STATUS_VOIDED + "' end from receipt_marks m"
			+ " where m.receipt_serial_no = ?), '" + STATUS_VALID + "')";

	private ReceiptTable() {
	}

	static TableDef<Receipt> build() {
		List<ColumnDef<Receipt>> columns = List.of(
				ColumnDef.<Receipt>number("serialNo", "Serial no."),
				ColumnDef.<Receipt>text("receiptNo", "Receipt no."),
				ColumnDef.<Receipt>date("receiptDate", "Date"),
				ColumnDef.<Receipt>text("student", "Student").path("studentName"),
				ColumnDef.<Receipt>text("grNo", "GR No.").searchable(false),
				ColumnDef.<Receipt>text("stdDiv", "Std / Div").expression(stdDiv()).searchable(false),
				ColumnDef.<Receipt>text("type", "Type")
					.expression(typeWords("Current year", "Previous years"))
					.searchable(false),
				ColumnDef.<Receipt>text("mode", "Mode").expression(modeWords()).searchable(false),
				ColumnDef.<Receipt>number("registrationPaid", "Registration fee"),
				ColumnDef.<Receipt>number("yearFee", "Year fee").expression(feeOfType("CURRENT_YEAR")),
				ColumnDef.<Receipt>number("previousYearsFee", "Previous years' fee")
					.expression(feeOfType("PREVIOUS_YEARS")),
				ColumnDef.<Receipt>number("total", "Total"),
				ColumnDef.<Receipt>text("status", "Status").expression(status()).searchable(false),
				ColumnDef.<Receipt>text("chequeNo", "Cheque no.").searchable(false).asHidden(),
				ColumnDef.<Receipt>date("chequeDate", "Cheque date").asHidden(),
				ColumnDef.<Receipt>text("bankName", "Bank").searchable(false).asHidden(),
				ColumnDef.<Receipt>number("studentId", "Student id").asInternal(),
				ColumnDef.<Receipt>number("standardId", "Standard id").asInternal());
		return TableDef.of(Receipt.class, columns).sortedBy("serialNo", -1);
	}

	private static ExpressionFactory<Receipt> stdDiv() {
		return (root, cb) -> {
			Expression<String> standard = root.get("standardName");
			Expression<String> division = root.get("divisionName");
			return cb.coalesce(cb.concat(cb.concat(standard, "-"), division), standard);
		};
	}

	private static ExpressionFactory<Receipt> typeWords(String current, String previous) {
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(
				"case ? when 'CURRENT_YEAR' then '" + current + "' else '" + previous + "' end", String.class,
				root.get("receiptType"));
	}

	private static ExpressionFactory<Receipt> modeWords() {
		return (root, cb) -> ((HibernateCriteriaBuilder) cb)
			.sql("case ? when 'CASH' then 'Cash' else 'Cheque' end", String.class, root.get("paymentMode"));
	}

	/** The fee amount of a receipt of the given type, zero for the other type. */
	private static ExpressionFactory<Receipt> feeOfType(String type) {
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(
				"case ? when '" + type + "' then ? else 0 end", BigDecimal.class, root.get("receiptType"),
				root.get("feePaid"));
	}

	private static ExpressionFactory<Receipt> status() {
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(STATUS_SQL, String.class, root.get("serialNo"));
	}

}
