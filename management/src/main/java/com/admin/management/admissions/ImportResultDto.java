package com.admin.management.admissions;

import java.util.List;

public record ImportResultDto(
		Long batchId,
		int total,
		int added,
		int skipped,
		int duplicates,
		List<ResponsesFileReader.InvalidRow> invalid
) {
}
