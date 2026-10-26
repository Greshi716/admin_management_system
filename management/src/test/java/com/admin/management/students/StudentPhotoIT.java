package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;
import com.admin.management.support.StudentTestData;

/** Photo upload, resize, replace and removal (FR-005) and its audit entries (FR-012). */
@Covers("FR-005")
@Covers("FR-012")
class StudentPhotoIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	MockHttpSession session;

	long studentId;

	@BeforeAll
	void masterData() {
		long standard = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		long category = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		studentId = StudentTestData.insertStudent(jdbc, StudentTestData.valid(1, category, standard));
	}

	@BeforeEach
	void signIn() throws Exception {
		session = SignIn.asTestAdmin(mvc);
		jdbc.update("delete from student_photos");
		jdbc.update("update students set photo_version = null");
	}

	private ResultActions upload(String name, String contentType, byte[] bytes) throws Exception {
		return mvc.perform(multipart(HttpMethod.PUT, "/api/students/" + studentId + "/photo")
			.file(new MockMultipartFile("file", name, contentType, bytes))
			.with(csrf())
			.session(session));
	}

	private static byte[] png(int width, int height, boolean transparent) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		if (!transparent) {
			g.setColor(new Color(200, 30, 30));
			g.fillRect(0, 0, width, height);
		}
		g.dispose();
		return write(image, "png");
	}

	private static byte[] jpeg(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(30, 30, 200));
		g.fillRect(0, 0, width, height);
		g.dispose();
		return write(image, "jpg");
	}

	private static byte[] write(BufferedImage image, String format) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private BufferedImage stored() throws Exception {
		byte[] content = jdbc.queryForObject("select content from student_photos where student_id = ?", byte[].class,
				studentId);
		return ImageIO.read(new ByteArrayInputStream(content));
	}

	@Test
	void aPngIsAcceptedScaledToFourHundredPixelsAndStoredAsJpeg() throws Exception {
		upload("photo.png", "image/png", png(1600, 1000, false)).andExpect(status().isOk())
			.andExpect(jsonPath("$.photoVersion").isNumber());

		BufferedImage image = stored();
		assertThat(image.getWidth()).isEqualTo(400);
		assertThat(image.getHeight()).isEqualTo(250);
		assertThat(jdbc.queryForObject("select content_type from student_photos", String.class)).isEqualTo("image/jpeg");
		assertThat(jdbc.queryForObject("select photo_version from students where id = ?", Long.class, studentId))
			.isNotNull();
	}

	@Test
	void aJpegIsAcceptedAndASmallPhotoIsNotEnlarged() throws Exception {
		upload("photo.jpg", "image/jpeg", jpeg(120, 160)).andExpect(status().isOk());

		BufferedImage image = stored();
		assertThat(image.getWidth()).isEqualTo(120);
		assertThat(image.getHeight()).isEqualTo(160);
	}

	@Test
	void transparentAreasBecomeWhite() throws Exception {
		upload("clear.png", "image/png", png(100, 100, true)).andExpect(status().isOk());

		int pixel = stored().getRGB(50, 50) & 0xFFFFFF;
		assertThat(pixel).isGreaterThan(0xF0F0F0);
	}

	@Test
	void aTextFileRenamedAsJpgIsRefusedBesideThePhoto() throws Exception {
		upload("photo.jpg", "image/jpeg", "this is not a picture".getBytes(StandardCharsets.UTF_8))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("JPEG or PNG")));

		assertThat(jdbc.queryForObject("select count(*) from student_photos", Integer.class)).isZero();
	}

	@Test
	void aGifIsRefusedEvenWithAJpegName() throws Exception {
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		upload("photo.jpg", "image/jpeg", write(image, "gif")).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void aFileOverFiveMegabytesIsRefused() throws Exception {
		byte[] big = new byte[5 * 1024 * 1024 + 1];
		upload("big.jpg", "image/jpeg", big).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("5 MB")));
	}

	@Test
	void anImageWithTooManyPixelsIsRefusedFromItsHeader() throws Exception {
		upload("huge.png", "image/png", pngHeaderOnly(6000, 5000)).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("too many pixels")));
	}

	@Test
	void anEmptyUploadIsRefused() throws Exception {
		upload("empty.png", "image/png", new byte[0]).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void aPhoneJpegWithARotationTagComesOutUpright() throws Exception {
		byte[] sideways = withExifOrientation(jpeg(300, 200), 6);

		upload("phone.jpg", "image/jpeg", sideways).andExpect(status().isOk());

		BufferedImage image = stored();
		assertThat(image.getWidth()).isEqualTo(200);
		assertThat(image.getHeight()).isEqualTo(300);
		assertThat(PhotoService.exifOrientation(sideways)).isEqualTo(6);
		assertThat(PhotoService.exifOrientation(jpeg(10, 10))).isEqualTo(1);
	}

	@Test
	void replacingChangesThePhotoVersionAndRemovingShowsNoPhoto() throws Exception {
		upload("a.png", "image/png", png(100, 100, false)).andExpect(status().isOk());
		long first = jdbc.queryForObject("select photo_version from students where id = ?", Long.class, studentId);
		Thread.sleep(5);
		upload("b.jpg", "image/jpeg", jpeg(200, 100)).andExpect(status().isOk());
		long second = jdbc.queryForObject("select photo_version from students where id = ?", Long.class, studentId);
		assertThat(second).isNotEqualTo(first);
		assertThat(jdbc.queryForObject("select count(*) from student_photos", Integer.class)).isEqualTo(1);
		assertThat(stored().getWidth()).isEqualTo(200);

		mvc.perform(get("/api/students/" + studentId + "/photo?v=" + second).session(session))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
			.andExpect(header().string("Cache-Control", containsString("immutable")))
			.andExpect(header().string("Cache-Control", containsString("private")));

		// without the current version the browser must ask again
		mvc.perform(get("/api/students/" + studentId + "/photo").session(session))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", containsString("no-cache")))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.not(containsString("immutable"))));
		mvc.perform(get("/api/students/" + studentId + "/photo?v=" + first).session(session))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.not(containsString("immutable"))));

		mvc.perform(delete("/api/students/" + studentId + "/photo").with(csrf()).session(session))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select photo_version from students where id = ?", Long.class, studentId)).isNull();
		mvc.perform(get("/api/students/" + studentId + "/photo").session(session)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/students/" + studentId + "/photo").with(csrf()).session(session))
			.andExpect(status().isNoContent());
	}

	@Test
	void photoChangesAreAuditedWithoutTheImageAndDoNotRaiseTheEditVersion() throws Exception {
		long versionBefore = jdbc.queryForObject("select version from students where id = ?", Long.class, studentId);
		int auditBefore = jdbc.queryForObject("select count(*) from audit_log where entity = ?", Integer.class,
				"Student#" + studentId);

		upload("a.png", "image/png", png(100, 100, false)).andExpect(status().isOk());
		upload("b.png", "image/png", png(120, 100, false)).andExpect(status().isOk());
		mvc.perform(delete("/api/students/" + studentId + "/photo").with(csrf()).session(session));

		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ?", Integer.class,
				"Student#" + studentId)).isEqualTo(auditBefore + 3);
		String changes = jdbc.queryForObject(
				"select string_agg(changes::text, ' ') from audit_log where entity = ? and area = 'Students'", String.class,
				"Student#" + studentId);
		assertThat(changes).contains("photo").contains("none").doesNotContain("JFIF");
		assertThat(jdbc.queryForObject("select version from students where id = ?", Long.class, studentId))
			.isEqualTo(versionBefore);
	}

	@Test
	void listQueriesNeverReadThePhotoBytes() {
		assertThat(java.util.Arrays.stream(Student.class.getDeclaredFields()).map(f -> f.getName()))
			.doesNotContain("content", "photo");
		assertThat(StudentTable.build(lookupForColumns()).columns()).extracting(c -> c.field()).doesNotContain("content",
				"photo");
	}

	@Autowired
	com.admin.management.masterdata.api.MasterDataLookup lookup;

	private com.admin.management.masterdata.api.MasterDataLookup lookupForColumns() {
		return lookup;
	}

	@Test
	void unknownStudentIsNotFoundAndAnonymousIsRefused() throws Exception {
		mvc.perform(multipart(HttpMethod.PUT, "/api/students/999999/photo")
			.file(new MockMultipartFile("file", "a.png", "image/png", png(10, 10, false)))
			.with(csrf())
			.session(session)).andExpect(status().isNotFound());
		mvc.perform(get("/api/students/" + studentId + "/photo")).andExpect(status().isUnauthorized());
	}

	/** A PNG whose header claims the given size; only the header is read before the pixel limit is checked. */
	private static byte[] pngHeaderOnly(int width, int height) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		writeInt(ihdr, width);
		writeInt(ihdr, height);
		ihdr.write(new byte[] { 8, 2, 0, 0, 0 });
		chunk(out, "IHDR", ihdr.toByteArray());
		Deflater deflater = new Deflater();
		deflater.setInput(new byte[] { 0 });
		deflater.finish();
		byte[] buffer = new byte[64];
		int length = deflater.deflate(buffer);
		chunk(out, "IDAT", java.util.Arrays.copyOf(buffer, length));
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
		writeInt(out, data.length);
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		out.write(typeBytes);
		out.write(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		writeInt(out, (int) crc.getValue());
	}

	private static void writeInt(ByteArrayOutputStream out, int value) {
		out.write(value >>> 24);
		out.write(value >>> 16);
		out.write(value >>> 8);
		out.write(value);
	}

	/** Inserts an EXIF APP1 segment that carries only the orientation tag, right after the JPEG start marker. */
	private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
		byte[] exif = { 'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1,
				0, (byte) orientation, 0, 0, 0, 0, 0, 0 };
		int length = exif.length + 2;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg[0]);
		out.write(jpeg[1]);
		out.write(0xFF);
		out.write(0xE1);
		out.write(length >>> 8);
		out.write(length);
		out.write(exif, 0, exif.length);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	@SuppressWarnings("unused")
	private static LocalDate unused() {
		return null;
	}

}
