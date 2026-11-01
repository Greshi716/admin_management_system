package com.admin.management.yearchange;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only record of a promotion run for one standard (FR-016, FR-017).
 */
@Entity
@Table(name = "promotion_runs")
public class PromotionRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "from_year_id", nullable = false, updatable = false)
	private Long fromYearId;

	@Column(name = "to_year_id", nullable = false, updatable = false)
	private Long toYearId;

	@Column(name = "standard_id", nullable = false, updatable = false)
	private Long standardId;

	@Column(name = "done_by", nullable = false, updatable = false)
	private Long doneBy;

	@Column(name = "done_at", nullable = false, updatable = false)
	private Instant doneAt = Instant.now();

	@Column(name = "promoted_count", nullable = false, updatable = false)
	private int promotedCount;

	@Column(name = "detained_count", nullable = false, updatable = false)
	private int detainedCount;

	@Column(name = "left_count", nullable = false, updatable = false)
	private int leftCount;

	@Column(name = "carried_total", nullable = false, updatable = false)
	private BigDecimal carriedTotal = BigDecimal.ZERO;

	protected PromotionRun() {
	}

	public PromotionRun(Long fromYearId, Long toYearId, Long standardId, Long doneBy,
			int promotedCount, int detainedCount, int leftCount, BigDecimal carriedTotal) {
		this.fromYearId = fromYearId;
		this.toYearId = toYearId;
		this.standardId = standardId;
		this.doneBy = doneBy;
		this.promotedCount = promotedCount;
		this.detainedCount = detainedCount;
		this.leftCount = leftCount;
		this.carriedTotal = carriedTotal;
	}

	public Long getId() {
		return id;
	}

	public Long getFromYearId() {
		return fromYearId;
	}

	public Long getToYearId() {
		return toYearId;
	}

	public Long getStandardId() {
		return standardId;
	}

	public Long getDoneBy() {
		return doneBy;
	}

	public Instant getDoneAt() {
		return doneAt;
	}

	public int getPromotedCount() {
		return promotedCount;
	}

	public int getDetainedCount() {
		return detainedCount;
	}

	public int getLeftCount() {
		return leftCount;
	}

	public BigDecimal getCarriedTotal() {
		return carriedTotal;
	}

}
