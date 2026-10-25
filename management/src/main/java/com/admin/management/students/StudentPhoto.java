package com.admin.management.students;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The resized photo of a student. Kept apart from {@link Student} so list queries never read the bytes. */
@Entity
@Table(name = "student_photos")
public class StudentPhoto {

	@Id
	@Column(name = "student_id")
	private Long studentId;

	@Column(nullable = false)
	private byte[] content;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private int sizeBytes;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StudentPhoto() {
	}

	public StudentPhoto(Long studentId, byte[] content, String contentType, Instant updatedAt) {
		this.studentId = studentId;
		replace(content, contentType, updatedAt);
	}

	public void replace(byte[] newContent, String newContentType, Instant now) {
		this.content = newContent;
		this.contentType = newContentType;
		this.sizeBytes = newContent.length;
		this.updatedAt = now;
	}

	public Long getStudentId() {
		return studentId;
	}

	public byte[] getContent() {
		return content;
	}

	public String getContentType() {
		return contentType;
	}

	public int getSizeBytes() {
		return sizeBytes;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "StudentPhoto[studentId=" + studentId + ", sizeBytes=" + sizeBytes + "]";
	}

}
