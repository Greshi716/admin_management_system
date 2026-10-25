package com.admin.management.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Angular app for every browser route (for example {@code /students/12} after a refresh) by
 * forwarding to {@code index.html}. Paths under {@code /api} are left alone, and so are paths whose last segment
 * has a file extension (scripts, styles, fonts, images), so a missing API route or asset still returns 404 and
 * real static files reach the resource handler.
 *
 * <p>
 * Spring cannot put a pattern on {@code **}, so the routes are listed by depth, up to five segments. Browser
 * routes are shallower than that; a deeper one would need another line here.
 */
@Controller
public class SpaFallbackController {

	@GetMapping({ "/", //
			"/{a:^(?!api$)[^.]*}", //
			"/{a:^(?!api$)[^.]*}/{b:[^.]*}", //
			"/{a:^(?!api$)[^.]*}/{b}/{c:[^.]*}", //
			"/{a:^(?!api$)[^.]*}/{b}/{c}/{d:[^.]*}", //
			"/{a:^(?!api$)[^.]*}/{b}/{c}/{d}/{e:[^.]*}" })
	public String forwardToIndex() {
		return "forward:/index.html";
	}

}
