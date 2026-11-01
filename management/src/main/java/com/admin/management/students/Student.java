package com.admin.management.students;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The permanent record of a student. Never deleted (constitution IV): leaving is the status {@code Left}.
 * Master data ids are plain numbers; the tables are linked by foreign keys only.
 */
@Entity
@Table(name = "students")
public class Student {

	public static final String ACTIVE = "Active";

	public static final String LEFT = "Left";

	public static final String MANUAL = "Manual";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "gr_no", nullable = false)
	private String grNo;

	/** Taken from the sequence by the database and never reused. */
	@Generated(event = EventType.INSERT)
	@Column(name = "sr_no", insertable = false, updatable = false)
	private Long srNo;

	@Column(nullable = false)
	private String surname;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "middle_name")
	private String middleName;

	@Column(nullable = false)
	private String gender;

	@Column(nullable = false)
	private LocalDate dob;

	@Column(name = "birth_place")
	private String birthPlace;

	@Column(name = "father_name")
	private String fatherName;

	@Column(name = "mother_name")
	private String motherName;

	@Column(name = "mobile_1", nullable = false)
	private String mobile1;

	@Column(name = "mobile_2")
	private String mobile2;

	@Column(nullable = false)
	private String address;

	private String aadhaar;

	@Column(name = "religion_caste")
	private String religionCaste;

	/** The category chosen at registration; later changes happen on the yearly class record. */
	@Column(name = "category_id", nullable = false, updatable = false)
	private Long categoryId;

	@Column(name = "admission_date", nullable = false)
	private LocalDate admissionDate;

	@Column(name = "admission_standard_id", nullable = false)
	private Long admissionStandardId;

	@Column(name = "previous_school")
	private String previousSchool;

	@Column(name = "photo_version")
	private Long photoVersion;

	@Column(nullable = false)
	private String status = ACTIVE;

	@Column(nullable = false, updatable = false)
	private String source = MANUAL;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Version
	private Long version;

	protected Student() {
	}

	public Student(StudentDetails details, Long categoryId) {
		this(details, categoryId, MANUAL);
	}

	public Student(StudentDetails details, Long categoryId, String source) {
		this.categoryId = categoryId;
		if (source != null) {
			this.source = source;
		}
		apply(details);
	}

	/** Copies the editable details; used on create and on edit. */
	public void apply(StudentDetails d) {
		this.grNo = d.grNo();
		this.surname = d.surname();
		this.firstName = d.firstName();
		this.middleName = d.middleName();
		this.gender = d.gender();
		this.dob = d.dob();
		this.birthPlace = d.birthPlace();
		this.fatherName = d.fatherName();
		this.motherName = d.motherName();
		this.mobile1 = d.mobile1();
		this.mobile2 = d.mobile2();
		this.address = d.address();
		this.aadhaar = d.aadhaar();
		this.religionCaste = d.religionCaste();
		this.admissionDate = d.admissionDate();
		this.admissionStandardId = d.admissionStandardId();
		this.previousSchool = d.previousSchool();
	}

	public StudentDetails details() {
		return new StudentDetails(grNo, surname, firstName, middleName, gender, dob, birthPlace, fatherName,
				motherName, mobile1, mobile2, address, aadhaar, religionCaste, admissionDate, admissionStandardId,
				previousSchool);
	}

	public void markLeft() {
		this.status = LEFT;
	}

	public void setPhotoVersion(Long photoVersion) {
		this.photoVersion = photoVersion;
	}

	public Long getId() {
		return id;
	}

	public String getGrNo() {
		return grNo;
	}

	public Long getSrNo() {
		return srNo;
	}

	public String getSurname() {
		return surname;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getMiddleName() {
		return middleName;
	}

	public String getGender() {
		return gender;
	}

	public LocalDate getDob() {
		return dob;
	}

	public String getBirthPlace() {
		return birthPlace;
	}

	public String getFatherName() {
		return fatherName;
	}

	public String getMotherName() {
		return motherName;
	}

	public String getMobile1() {
		return mobile1;
	}

	public String getMobile2() {
		return mobile2;
	}

	public String getAddress() {
		return address;
	}

	public String getAadhaar() {
		return aadhaar;
	}

	public String getReligionCaste() {
		return religionCaste;
	}

	public Long getCategoryId() {
		return categoryId;
	}

	public LocalDate getAdmissionDate() {
		return admissionDate;
	}

	public Long getAdmissionStandardId() {
		return admissionStandardId;
	}

	public String getPreviousSchool() {
		return previousSchool;
	}

	public Long getPhotoVersion() {
		return photoVersion;
	}

	public String getStatus() {
		return status;
	}

	public String getSource() {
		return source;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Long getVersion() {
		return version;
	}

	/** Field values for the audit log. Aadhaar and religion/caste are masked by the audit service when stored. */
	public Map<String, Object> snapshot() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("grNo", grNo);
		values.put("surname", surname);
		values.put("firstName", firstName);
		values.put("middleName", middleName);
		values.put("gender", gender);
		values.put("dob", dob == null ? null : dob.toString());
		values.put("birthPlace", birthPlace);
		values.put("fatherName", fatherName);
		values.put("motherName", motherName);
		values.put("mobile1", mobile1);
		values.put("mobile2", mobile2);
		values.put("address", address);
		values.put("aadhaar", aadhaar);
		values.put("religionCaste", religionCaste);
		values.put("categoryId", categoryId);
		values.put("admissionDate", admissionDate == null ? null : admissionDate.toString());
		values.put("admissionStandardId", admissionStandardId);
		values.put("previousSchool", previousSchool);
		values.put("status", status);
		values.put("source", source);
		return values;
	}

	/** Never prints Aadhaar or religion/caste (constitution VI). */
	@Override
	public String toString() {
		return "Student[id=" + id + ", grNo=" + grNo + ", status=" + status + "]";
	}

}
