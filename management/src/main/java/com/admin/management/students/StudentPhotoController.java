package com.admin.management.students;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** {@code /api/students/{id}/photo} (FR-005). */
@RestController
@RequestMapping("/api/students/{id}/photo")
class StudentPhotoController {

	private final PhotoService photos;

	StudentPhotoController(PhotoService photos) {
		this.photos = photos;
	}

	/**
	 * The stored JPEG. The query parameter {@code v} is the photo version: only when it is the current version can the
	 * answer be cached for a year; without it, or with an old one, the browser must ask again.
	 */
	@GetMapping
	ResponseEntity<byte[]> get(@PathVariable Long id, @RequestParam(required = false) Long v) {
		StudentPhoto photo = photos.get(id);
		boolean current = v != null && v.equals(photos.currentVersion(id));
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(photo.getContentType()))
			.cacheControl(current ? CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable()
					: CacheControl.noCache().cachePrivate())
			.body(photo.getContent());
	}

	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Map<String, Long> put(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
		return Map.of("photoVersion", photos.put(id, file));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		photos.remove(id);
	}

}
