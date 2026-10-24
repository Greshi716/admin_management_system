package com.admin.management.shared.table;

import java.util.List;
import java.util.Map;

/** One page of a list and the total number of rows that match (FR-029). */
public record TablePage(List<Map<String, Object>> rows, long total) {

}
