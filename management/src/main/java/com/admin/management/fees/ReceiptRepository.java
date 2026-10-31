package com.admin.management.fees;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Receipts are only added and read; there is no delete and no update (FR-009). */
public interface ReceiptRepository extends Repository<Receipt, Long> {

	<S extends Receipt> S save(S receipt);

	<S extends Receipt> S saveAndFlush(S receipt);

	Optional<Receipt> findById(Long serialNo);

	/** Every receipt with this number, whatever the case; more than one only when the earlier one was voided. */
	List<Receipt> findByReceiptNoIgnoreCase(String receiptNo);

	List<Receipt> findByStudentIdOrderBySerialNoDesc(Long studentId);

}
