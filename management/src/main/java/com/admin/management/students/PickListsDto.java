package com.admin.management.students;

import java.util.List;

import com.admin.management.masterdata.api.MasterDataLookup.CategoryOption;
import com.admin.management.masterdata.api.MasterDataLookup.DivisionOption;
import com.admin.management.masterdata.api.MasterDataLookup.StandardOption;

/** The choices of the forms and filters (contracts/openapi.yaml {@code PickLists}). */
public record PickListsDto(CurrentYear currentYear, List<StandardOption> standards, List<DivisionOption> divisions,
		List<CategoryOption> categories) {

	public record CurrentYear(Long id, String name) {
	}

}
