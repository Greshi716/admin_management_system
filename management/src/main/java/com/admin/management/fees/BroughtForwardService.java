package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.StaleVersionException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.StudentLookup;

/**
 * Saves the one brought-forward amount and note of a student (FR-007). A student has at most one row; clearing saves
 * 0 and no note and never deletes it. Finalising and revising a structure never touch it. The audit entry is written
 * in the same transaction (FR-012); it holds the old and new amount in full and, for the note, only that it changed.
 */
@Service
class BroughtForwardService {

	private static final BigDecimal ZERO = new BigDecimal("0.00");

	private final StudentLookup students;

	private final BroughtForwardRepository repository;

	private final FeesAudit audit;

	private final Clock clock;

	BroughtForwardService(StudentLookup students, BroughtForwardRepository repository, FeesAudit audit, Clock clock) {
		this.students = students;
		this.repository = repository;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	StudentFeesDto.BroughtForwardDto save(Long studentId, BroughtForwardInput in) {
		if (!students.exists(studentId)) {
			throw new NotFoundException("Student", studentId);
		}
		String note = cleanNote(in.note());
		validate(in.amount(), note);
		BigDecimal amount = Money.scale2(in.amount());

		Optional<BroughtForward> existing = repository.findById(studentId);
		if (existing.isEmpty()) {
			if (in.version() != null) {
				throw new StaleVersionException(new StudentFeesDto.BroughtForwardDto(null, ZERO, null));
			}
			if (amount.signum() == 0 && note == null) {
				return new StudentFeesDto.BroughtForwardDto(null, ZERO, null); // nothing to keep: no amount, no note
			}
			try {
				BroughtForward created = repository.saveAndFlush(new BroughtForward(studentId, amount, note, clock.instant()));
				audit.broughtForwardSaved(studentId, null, amount, note != null);
				return dto(created);
			}
			catch (DataIntegrityViolationException raced) {
				throw new StaleVersionException(current(studentId)); // another window saved the first amount
			}
		}

		BroughtForward row = existing.get();
		if (!Objects.equals(in.version(), row.getVersion())) {
			throw new StaleVersionException(dto(row));
		}
		BigDecimal oldAmount = row.getAmount();
		boolean noteChanged = !Objects.equals(row.getNote(), note);
		if (oldAmount.compareTo(amount) == 0 && !noteChanged) {
			return dto(row); // nothing changed: no new version, no audit entry
		}
		row.change(amount, note, clock.instant());
		BroughtForward saved = repository.saveAndFlush(row);
		audit.broughtForwardSaved(studentId, oldAmount, amount, noteChanged);
		return dto(saved);
	}

	private StudentFeesDto.BroughtForwardDto current(Long studentId) {
		return repository.findById(studentId).map(BroughtForwardService::dto)
			.orElse(new StudentFeesDto.BroughtForwardDto(null, ZERO, null));
	}

	private static StudentFeesDto.BroughtForwardDto dto(BroughtForward row) {
		return new StudentFeesDto.BroughtForwardDto(row.getVersion(), Money.scale2(row.getAmount()), row.getNote());
	}

	/** The note without surrounding spaces; blank means no note. */
	private static String cleanNote(String note) {
		if (note == null) {
			return null;
		}
		String trimmed = note.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** The amount is zero or more with at most two decimals; it is refused, never rounded (FR-011). */
	private static void validate(BigDecimal amount, String note) {
		List<FieldError> errors = new ArrayList<>();
		if (amount == null) {
			errors.add(new FieldError("amount", "Enter the amount, or 0 if nothing is brought forward."));
		}
		else if (amount.signum() < 0) {
			errors.add(new FieldError("amount", "The amount cannot be negative."));
		}
		else if (amount.stripTrailingZeros().scale() > 2) {
			errors.add(new FieldError("amount", "Use at most two decimals, for example 1250.50."));
		}
		else if (amount.precision() - amount.scale() > Money.MAX_INTEGER_DIGITS) {
			errors.add(new FieldError("amount", Money.TOO_LARGE_MESSAGE));
		}
		if (note != null && note.length() > BroughtForward.NOTE_MAX) {
			errors.add(new FieldError("note", "The note can be at most " + BroughtForward.NOTE_MAX + " characters."));
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
	}

}
