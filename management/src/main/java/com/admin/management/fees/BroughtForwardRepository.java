package com.admin.management.fees;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Declares only what the feature needs: there is no delete method, "clear" saves zero (constitution IV). */
public interface BroughtForwardRepository extends Repository<BroughtForward, Long> {

	<S extends BroughtForward> S save(S broughtForward);

	<S extends BroughtForward> S saveAndFlush(S broughtForward);

	Optional<BroughtForward> findById(Long studentId);

}
