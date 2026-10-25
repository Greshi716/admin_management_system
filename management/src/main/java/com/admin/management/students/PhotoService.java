package com.admin.management.students;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.ValidationException;

/**
 * Adds, replaces, reads and removes a student's photo (FR-005). The server decides what a file is by decoding it, not
 * by its name; it refuses files over 5 MB or over 25 megapixels (read from the header, before decoding), turns phone
 * photos upright, scales the longest side to 400 px and stores a JPEG (research R2). Photo changes are audited
 * without the image and do not change the student's edit version, so they never make an open edit form stale.
 */
@Service
public class PhotoService {

	static final long MAX_BYTES = 5L * 1024 * 1024;

	static final long MAX_PIXELS = 25_000_000L;

	static final int MAX_SIDE = 400;

	static final float JPEG_QUALITY = 0.85f;

	private static final Set<String> FORMATS = Set.of("jpeg", "png");

	private final StudentRepository students;

	private final PhotoRepository photos;

	private final StudentAudit audit;

	private final Clock clock;

	PhotoService(StudentRepository students, PhotoRepository photos, StudentAudit audit, Clock clock) {
		this.students = students;
		this.photos = photos;
		this.audit = audit;
		this.clock = clock;
	}

	/** Stores the photo and returns its new version, which changes on every add or replace. */
	@Transactional
	public long put(Long studentId, MultipartFile file) {
		Student student = find(studentId);
		byte[] original = read(file);
		byte[] jpeg = toJpeg(original);
		Long previous = student.getPhotoVersion();
		// time-based so a removed and re-added photo never reuses a cached address; always above the previous one
		long version = previous == null ? clock.millis() : Math.max(clock.millis(), previous + 1);
		Optional<StudentPhoto> existing = photos.findById(studentId);
		if (existing.isPresent()) {
			existing.get().replace(jpeg, "image/jpeg", clock.instant());
			photos.save(existing.get());
		}
		else {
			photos.save(new StudentPhoto(studentId, jpeg, "image/jpeg", clock.instant()));
		}
		students.setPhotoVersion(studentId, version);
		audit.photoChanged(studentId, previous, version);
		return version;
	}

	@Transactional(readOnly = true)
	public StudentPhoto get(Long studentId) {
		return photos.findById(studentId).orElseThrow(() -> new NotFoundException("Photo", studentId));
	}

	/** Removes the photo; the initials are shown again. Nothing happens when there is no photo. */
	@Transactional
	public void remove(Long studentId) {
		Student student = find(studentId);
		if (photos.findById(studentId).isEmpty()) {
			return;
		}
		Long previous = student.getPhotoVersion();
		photos.deleteById(studentId);
		students.setPhotoVersion(studentId, null);
		audit.photoChanged(studentId, previous, null);
	}

	private Student find(Long studentId) {
		return students.findById(studentId).orElseThrow(() -> new NotFoundException("Student", studentId));
	}

	private static byte[] read(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ValidationException("file", "Choose a photo to upload.");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ValidationException("file", "The photo is larger than 5 MB.");
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new ValidationException("file", "The photo could not be read.");
		}
	}

	/** Decodes a JPEG or PNG, makes it upright, scales it down and writes it as a JPEG. */
	static byte[] toJpeg(byte[] original) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
			Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
			if (readers == null || !readers.hasNext()) {
				throw notAPhoto();
			}
			ImageReader reader = readers.next();
			try {
				if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw notAPhoto();
				}
				reader.setInput(input, true, true);
				long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
				if (pixels > MAX_PIXELS) {
					throw new ValidationException("file", "The photo has too many pixels (more than 25 megapixels).");
				}
				BufferedImage decoded = reader.read(0);
				BufferedImage upright = orient(decoded, exifOrientation(original));
				return encode(scale(upright));
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ValidationException validation) {
				throw validation;
			}
			throw notAPhoto();
		}
	}

	private static ValidationException notAPhoto() {
		return new ValidationException("file", "The file must be a JPEG or PNG photo.");
	}

	/** Draws on white, so transparent PNG areas do not turn black in the JPEG. */
	private static BufferedImage onWhite(BufferedImage source, int width, int height, AffineTransform transform,
			Object interpolation) {
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = target.createGraphics();
		try {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, width, height);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, transform, null);
		}
		finally {
			g.dispose();
		}
		return target;
	}

	/** Applies the EXIF orientation (1 to 8) that phone cameras write; the JDK reader ignores it. */
	static BufferedImage orient(BufferedImage image, int orientation) {
		int w = image.getWidth();
		int h = image.getHeight();
		AffineTransform t = switch (orientation) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
			case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
			case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
			case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
			case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
			default -> null;
		};
		if (t == null) {
			return image;
		}
		boolean swap = orientation >= 5;
		return onWhite(image, swap ? h : w, swap ? w : h, t, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
	}

	/** Halves the size while it is more than twice too big, then makes the last step; never enlarges. */
	static BufferedImage scale(BufferedImage image) {
		int longest = Math.max(image.getWidth(), image.getHeight());
		BufferedImage current = image;
		if (longest <= MAX_SIDE) {
			return current.getType() == BufferedImage.TYPE_INT_RGB ? current
					: onWhite(current, current.getWidth(), current.getHeight(), new AffineTransform(),
							RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		double ratio = (double) MAX_SIDE / longest;
		int targetW = Math.max(1, (int) Math.round(image.getWidth() * ratio));
		int targetH = Math.max(1, (int) Math.round(image.getHeight() * ratio));
		while (current.getWidth() > targetW * 2 || current.getHeight() > targetH * 2) {
			int w = Math.max(targetW, current.getWidth() / 2);
			int h = Math.max(targetH, current.getHeight() / 2);
			current = onWhite(current, w, h,
					AffineTransform.getScaleInstance((double) w / current.getWidth(), (double) h / current.getHeight()),
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		return onWhite(current, targetW, targetH, AffineTransform.getScaleInstance(
				(double) targetW / current.getWidth(), (double) targetH / current.getHeight()),
				RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	}

	private static byte[] encode(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		try {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
				writer.setOutput(stream);
				writer.write(null, new IIOImage(image, null, null), param);
			}
			return out.toByteArray();
		}
		finally {
			writer.dispose();
		}
	}

	/** The EXIF orientation of a JPEG (1 to 8), or 1 when there is none or the file is not a JPEG. */
	static int exifOrientation(byte[] jpeg) {
		if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
			return 1;
		}
		int i = 2;
		while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
			int marker = jpeg[i + 1] & 0xFF;
			if (marker == 0xDA || marker == 0xD9) {
				break;
			}
			int length = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
			if (marker == 0xE1 && length >= 16 && i + 4 + 6 <= jpeg.length && jpeg[i + 4] == 'E' && jpeg[i + 5] == 'x'
					&& jpeg[i + 6] == 'i' && jpeg[i + 7] == 'f') {
				return tiffOrientation(jpeg, i + 10, Math.min(jpeg.length, i + 2 + length));
			}
			i += 2 + length;
		}
		return 1;
	}

	private static int tiffOrientation(byte[] b, int tiff, int end) {
		if (tiff + 8 > end) {
			return 1;
		}
		boolean big = b[tiff] == 'M';
		if (!big && b[tiff] != 'I') {
			return 1;
		}
		long ifd = tiff + u32(b, tiff + 4, big);
		if (ifd + 2 > end) {
			return 1;
		}
		int entries = u16(b, (int) ifd, big);
		for (int n = 0; n < entries; n++) {
			int entry = (int) ifd + 2 + n * 12;
			if (entry + 12 > end) {
				return 1;
			}
			if (u16(b, entry, big) == 0x0112) {
				int value = u16(b, entry + 8, big);
				return value >= 1 && value <= 8 ? value : 1;
			}
		}
		return 1;
	}

	private static int u16(byte[] b, int at, boolean big) {
		int first = b[at] & 0xFF;
		int second = b[at + 1] & 0xFF;
		return big ? (first << 8) | second : (second << 8) | first;
	}

	private static long u32(byte[] b, int at, boolean big) {
		long value = 0;
		for (int k = 0; k < 4; k++) {
			int octet = b[big ? at + k : at + 3 - k] & 0xFF;
			value = (value << 8) | octet;
		}
		return value;
	}

}
