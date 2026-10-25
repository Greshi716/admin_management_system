package com.admin.management.shared.table;

import java.util.Map;

/** Records that an export happened. Declared here so {@code shared} does not depend on the audit module. */
public interface ExportRecorder {

	void recordExport(String area, String listName, Map<String, Object> changes);

}
