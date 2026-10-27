package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Dues of earlier years: one amount and a short note per student (FR-007). "Clear" stores 0 and no note; the row is
 * never deleted.
 */
@Entity
@Table(name = "fee_brought_forward")
public class BroughtForward {

	public static final int NOTE_MAX = 200;

	@Id
	@Column(name = "student_id")
	private Long studentId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	private String note;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected BroughtForward() {
	}

	public BroughtForward(Long studentId, BigDecimal amount, String note, Instant now) {
		this.studentId = studentId;
		change(amount, note, now);
	}

	public void change(BigDecimal newAmount, String newNote, Instant now) {
		this.amount = Money.scale2(newAmount);
		this.note = newNote;
		this.updatedAt = now;
	}

	public Long getStudentId() {
		return studentId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getNote() {
		return note;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getVersion() {
		return version;
	}

}
