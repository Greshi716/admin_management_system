package com.admin.management.fees;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.admin.management.audit.AuditService;

/**
 * Writes the audit entries of fee changes in the caller's transaction, so the change and its entry are saved
 * together or not at all (FR-012). The entity column holds {@code FeeRow#id}, {@code FeeStructure#yearId} or
 * {@code BroughtForward#studentId}. Amounts are kept in full as text with two decimals. The text of a
 * brought-forward note is never stored: the entry only says that the note changed.
 */
@Component
class FeesAudit {

	static final String AREA = "Fees";

	static final String FEE_ROW = "FeeRow";

	static final String FEE_STRUCTURE = "FeeStructure";

	static final String BROUGHT_FORWARD = "BroughtForward";

	/** The reason of an entry written for a row change in a finalised year. */
	static final String REVISE = "Revise";

	/** How many class records a revise looked at and how many accounts it actually changed. */
	record Recalculated(int studentsRecalculated, int accountsChanged) {
	}

	private final AuditService audit;

	FeesAudit(AuditService audit) {
		this.audit = audit;
	}

	/** {@code reason} and {@code recalculated} are null for a row change in a Draft year. */
	void rowCreated(Long rowId, Map<String, Object> values, String reason, Recalculated recalculated) {
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("new", values);
		write("Create", FEE_ROW + "#" + rowId, changes, reason, recalculated);
	}

	/** Records only the fields that changed. A save that changes nothing writes no entry. */
	void rowEdited(Long rowId, Map<String, Object> before, Map<String, Object> after, String reason,
			Recalculated recalculated) {
		Map<String, Object> oldValues = new LinkedHashMap<>();
		Map<String, Object> newValues = new LinkedHashMap<>();
		after.forEach((field, value) -> {
			if (!Objects.equals(before.get(field), value)) {
				oldValues.put(field, before.get(field));
				newValues.put(field, value);
			}
		});
		if (newValues.isEmpty()) {
			return;
		}
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("old", oldValues);
		changes.put("new", newValues);
		write("Edit", FEE_ROW + "#" + rowId, changes, reason, recalculated);
	}

	void rowDeleted(Long rowId, Map<String, Object> values, String reason, Recalculated recalculated) {
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("old", values);
		write("Delete", FEE_ROW + "#" + rowId, changes, reason, recalculated);
	}

	/** The structure of a year was finalised: how many class records got an account, how many accounts, how many rows. */
	void finalised(Long yearId, int studentsRecalculated, int accountsChanged, int rows) {
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("studentsRecalculated", studentsRecalculated);
		changes.put("accountsChanged", accountsChanged);
		changes.put("rows", rows);
		audit.record(AREA, "Finalise", FEE_STRUCTURE + "#" + yearId, changes, null);
	}

	/**
	 * A student's brought-forward amount or note was saved. {@code oldAmount} is null the first time (a Create).
	 * Otherwise only a changed amount (old and new, in full) and a changed note (the flag, never the text) are
	 * written, and a save that changes neither writes no entry.
	 */
	void broughtForwardSaved(Long studentId, BigDecimal oldAmount, BigDecimal newAmount, boolean noteChanged) {
		String entity = BROUGHT_FORWARD + "#" + studentId;
		Map<String, Object> changes = new LinkedHashMap<>();
		if (oldAmount == null) {
			changes.put("new", new LinkedHashMap<>(Map.of("amount", Money.text(newAmount))));
			if (noteChanged) {
				changes.put("noteChanged", true);
			}
			audit.record(AREA, "Create", entity, changes, null);
			return;
		}
		boolean amountChanged = oldAmount.compareTo(newAmount) != 0;
		if (!amountChanged && !noteChanged) {
			return;
		}
		if (amountChanged) {
			changes.put("old", new LinkedHashMap<>(Map.of("amount", Money.text(oldAmount))));
			changes.put("new", new LinkedHashMap<>(Map.of("amount", Money.text(newAmount))));
		}
		if (noteChanged) {
			changes.put("noteChanged", true);
		}
		audit.record(AREA, "Edit", entity, changes, null);
	}

	private void write(String action, String entity, Map<String, Object> changes, String reason,
			Recalculated recalculated) {
		if (recalculated != null) {
			changes.put("studentsRecalculated", recalculated.studentsRecalculated());
			changes.put("accountsChanged", recalculated.accountsChanged());
		}
		audit.record(AREA, action, entity, changes, reason);
	}

}
