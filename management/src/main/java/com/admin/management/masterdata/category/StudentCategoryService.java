package com.admin.management.masterdata.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.ActiveInput;
import com.admin.management.masterdata.MasterDataAudit;
import com.admin.management.masterdata.Versions;
import com.admin.management.shared.web.NotFoundException;

/** Student categories: create, edit, delete when unused, mark active or inactive (FR-018 to FR-021). */
@Service
public class StudentCategoryService {

	static final String TYPE = "StudentCategory";

	private final StudentCategoryRepository categories;

	private final MasterDataAudit audit;

	StudentCategoryService(StudentCategoryRepository categories, MasterDataAudit audit) {
		this.categories = categories;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<StudentCategoryDto> listActive() {
		return categories.findAllByActiveTrueOrderByIdAsc().stream().map(StudentCategoryDto::of).toList();
	}

	@Transactional
	public StudentCategoryDto create(StudentCategoryDto input) {
		StudentCategory category = categories.saveAndFlush(
				new StudentCategory(input.code().trim(), input.name().trim(), input.affectsFees()));
		audit.created(TYPE, category.getId(), category.snapshot());
		return StudentCategoryDto.of(category);
	}

	@Transactional
	public StudentCategoryDto update(Long id, StudentCategoryDto input) {
		StudentCategory category = find(id);
		Versions.check(input.version(), category.getVersion(), () -> StudentCategoryDto.of(category));
		var before = category.snapshot();
		category.change(input.code().trim(), input.name().trim(), input.affectsFees());
		categories.saveAndFlush(category);
		audit.edited(TYPE, id, before, category.snapshot());
		return StudentCategoryDto.of(category);
	}

	@Transactional
	public StudentCategoryDto setActive(Long id, ActiveInput input) {
		StudentCategory category = find(id);
		Versions.check(input.version(), category.getVersion(), () -> StudentCategoryDto.of(category));
		var before = category.snapshot();
		category.setActive(input.active());
		categories.saveAndFlush(category);
		audit.edited(TYPE, id, before, category.snapshot());
		return StudentCategoryDto.of(category);
	}

	@Transactional
	public void delete(Long id) {
		StudentCategory category = find(id);
		var values = category.snapshot();
		categories.delete(category);
		categories.flush();
		audit.deleted(TYPE, id, values);
	}

	private StudentCategory find(Long id) {
		return categories.findById(id).orElseThrow(() -> new NotFoundException("Student category", id));
	}

}
