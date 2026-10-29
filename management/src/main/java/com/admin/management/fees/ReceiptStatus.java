package com.admin.management.fees;

/** What a receipt counts as: Valid when it has no mark, otherwise the kind of its mark (FR-009). */
public enum ReceiptStatus {

	VALID, CHEQUE_NOT_CLEARED, VOIDED;

	static ReceiptStatus of(ReceiptMark mark) {
		if (mark == null) {
			return VALID;
		}
		return mark.getKind() == ReceiptMark.Kind.CHEQUE_NOT_CLEARED ? CHEQUE_NOT_CLEARED : VOIDED;
	}

}
