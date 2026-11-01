package com.admin.management.masterdata.standard;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.ActiveInput;
import com.admin.management.masterdata.MasterDataAudit;
import com.admin.management.masterdata.Versions;
import com.admin.management.shared.web.NotFoundException;

/** Standards: create, edit, delete when unused, mark active or inactive (FR-014, FR-015, FR-020, FR-021). */
@Service
public class StandardService {

	static final String TYPE = "Standard";

	private final StandardRepository standards;

	private final MasterDataAudit audit;

	StandardService(StandardRepository standards, MasterDataAudit audit) {
		this.standards = standards;
		this.audit = audit;
	}

	/** Active standards in display order, for choosing on new records. */
	@Transactional(readOnly = true)
	public List<StandardDto> listActive() {
		return standards.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream().map(StandardDto::of).toList();
	}

	@Transactional
	public StandardDto create(StandardDto input) {
		boolean usesStream = Boolean.TRUE.equals(input.usesStream());
		Standard standard = standards
			.saveAndFlush(new Standard(input.name().trim(), input.displayOrder(), input.stream(), usesStream));
		audit.created(TYPE, standard.getId(), standard.snapshot());
		return StandardDto.of(standard);
	}

	@Transactional
	public StandardDto update(Long id, StandardDto input) {
		Standard standard = find(id);
		Versions.check(input.version(), standard.getVersion(), () -> StandardDto.of(standard));
		var before = standard.snapshot();
		boolean usesStream = input.usesStream() != null ? input.usesStream() : standard.isUsesStream();
		standard.change(input.name().trim(), input.displayOrder(), input.stream(), usesStream);
		standards.saveAndFlush(standard);
		audit.edited(TYPE, id, before, standard.snapshot());
		return StandardDto.of(standard);
	}

	@Transactional
	public StandardDto setActive(Long id, ActiveInput input) {
		Standard standard = find(id);
		Versions.check(input.version(), standard.getVersion(), () -> StandardDto.of(standard));
		var before = standard.snapshot();
		standard.setActive(input.active());
		standards.saveAndFlush(standard);
		audit.edited(TYPE, id, before, standard.snapshot());
		return StandardDto.of(standard);
	}

	@Transactional
	public void delete(Long id) {
		Standard standard = find(id);
		var values = standard.snapshot();
		standards.delete(standard);
		standards.flush();
		audit.deleted(TYPE, id, values);
	}

	Standard find(Long id) {
		return standards.findById(id).orElseThrow(() -> new NotFoundException("Standard", id));
	}

}
