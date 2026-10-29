package com.admin.management.fees;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Marks are only added and read (FR-009). */
public interface ReceiptMarkRepository extends Repository<ReceiptMark, Long> {

	<S extends ReceiptMark> S save(S mark);

	<S extends ReceiptMark> S saveAndFlush(S mark);

	Optional<ReceiptMark> findByReceiptSerialNo(Long receiptSerialNo);

	List<ReceiptMark> findByReceiptSerialNoIn(Collection<Long> receiptSerialNos);

}
