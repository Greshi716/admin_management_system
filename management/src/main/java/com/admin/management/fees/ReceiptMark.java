package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Says that a receipt no longer counts as paid (FR-009): the cheque did not clear, or the developer voided it. A
 * receipt with no mark is valid. A mark is never updated or deleted, and a receipt has at most one.
 */
@Entity
@Immutable
@Table(name = "receipt_marks")
public class ReceiptMark {

	public enum Kind {

		CHEQUE_NOT_CLEARED, DEVELOPER_VOID

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(updatable = false)
	private Long id;

	@Column(name = "receipt_serial_no", nullable = false, updatable = false)
	private Long receiptSerialNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Kind kind;

	@Column(nullable = false, updatable = false)
	private String reason;

	@Column(name = "amount_added_back", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal amountAddedBack;

	/** Null for a developer void, which has no signed-in user. */
	@Column(name = "marked_by", updatable = false)
	private Long markedBy;

	@Column(name = "marked_at", nullable = false, updatable = false)
	private Instant markedAt;

	protected ReceiptMark() {
	}

	public ReceiptMark(Long receiptSerialNo, Kind kind, String reason, BigDecimal amountAddedBack, Long markedBy,
			Instant markedAt) {
		this.receiptSerialNo = receiptSerialNo;
		this.kind = kind;
		this.reason = reason;
		this.amountAddedBack = Money.scale2(amountAddedBack);
		this.markedBy = markedBy;
		this.markedAt = markedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getReceiptSerialNo() {
		return receiptSerialNo;
	}

	public Kind getKind() {
		return kind;
	}

	public String getReason() {
		return reason;
	}

	public BigDecimal getAmountAddedBack() {
		return amountAddedBack;
	}

	public Long getMarkedBy() {
		return markedBy;
	}

	public Instant getMarkedAt() {
		return markedAt;
	}

}
