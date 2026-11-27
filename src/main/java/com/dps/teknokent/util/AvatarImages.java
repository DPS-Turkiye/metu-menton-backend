package com.dps.teknokent.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.jspecify.annotations.Nullable;

/** Shrinks a profile photo for an avatar. The stored original is never changed; this works on a copy of its bytes. */
public final class AvatarImages {

	/** The longer side of an avatar photo: twice the largest avatar the web draws, for sharp high-density screens. */
	public static final int MAX_SIDE = 256;

	public static final String PNG = "image/png";
	public static final String JPEG = "image/jpeg";
	public static final String WEBP = "image/webp";

	// A photo is at most 5 MB, but a small file can still claim a huge canvas; beyond this it is not decoded.
	static final long MAX_PIXELS = 16_000_000L;

	// The JDK cannot shrink a WebP, so one goes out as uploaded only while it is small enough for a list of avatars.
	static final int MAX_WEBP_BYTES = 512 * 1024;

	private static final int EXIF_ORIENTATION_TAG = 0x0112;

	private AvatarImages() {
	}

	/** A photo as the avatar endpoint sends it. */
	@SuppressWarnings("ArrayRecordComponent") // Carries a photo's bytes to the controller; never compared or hashed.
	public record Image(byte[] bytes, String contentType) {
	}

	/**
	 * The photo at most {@link #MAX_SIDE} pixels on its longer side, turned upright when the camera recorded a
	 * rotation. A PNG or JPEG is always drawn afresh, so its metadata (a phone's GPS position, say) never leaves with
	 * it. A WebP, which the JDK cannot read, goes out as uploaded while it is small. Empty when the bytes are not a
	 * photo that can be shown, or one too large to decode safely.
	 */
	public static Optional<Image> thumbnail(byte[] original, @Nullable String contentType) {
		String type = contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
		if (type.equals(WEBP)) {
			return isWebp(original) && original.length <= MAX_WEBP_BYTES ? Optional.of(new Image(original, WEBP))
					: Optional.empty();
		}
		if (!type.equals(PNG) && !type.equals(JPEG)) {
			return Optional.empty();
		}
		// In memory: ImageIO's default stream would write a temporary file for every request.
		try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return Optional.empty();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
					return Optional.empty();
				}
				String format = reader.getFormatName().toLowerCase(Locale.ROOT);
				int orientation = format.contains("jp") ? jpegOrientation(original) : 1;
				// Reading every n-th pixel keeps the decoded raster small: still at least twice the avatar's size.
				int step = Math.max(1, Math.max(width, height) / (MAX_SIDE * 2));
				ImageReadParam param = reader.getDefaultReadParam();
				param.setSourceSubsampling(step, step, 0, 0);
				BufferedImage decoded = reader.read(0, param);
				return Optional.of(encode(upright(shrink(decoded), orientation)));
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException unreadable) {
			return Optional.empty();
		}
	}

	private static BufferedImage shrink(BufferedImage source) {
		int longer = Math.max(source.getWidth(), source.getHeight());
		if (longer <= MAX_SIDE) {
			return copy(source, source.getWidth(), source.getHeight());
		}
		double scale = (double) MAX_SIDE / longer;
		int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
		// Halving step by step keeps a large photo from turning grainy in one big jump.
		BufferedImage current = source;
		while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
			current = copy(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		return copy(current, targetWidth, targetHeight);
	}

	private static BufferedImage copy(BufferedImage source, int width, int height) {
		BufferedImage target = new BufferedImage(width, height, imageType(source));
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	/** EXIF orientations 2 to 8: mirrored and/or turned by 90, 180 or 270 degrees. */
	static BufferedImage upright(BufferedImage image, int orientation) {
		if (orientation < 2 || orientation > 8) {
			return image;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		boolean swaps = orientation >= 5;
		AffineTransform transform = new AffineTransform();
		switch (orientation) {
		case 2 -> transform.setTransform(-1, 0, 0, 1, width, 0);
		case 3 -> transform.setTransform(-1, 0, 0, -1, width, height);
		case 4 -> transform.setTransform(1, 0, 0, -1, 0, height);
		case 5 -> transform.setTransform(0, 1, 1, 0, 0, 0);
		case 6 -> transform.setTransform(0, 1, -1, 0, height, 0);
		case 7 -> transform.setTransform(0, -1, -1, 0, height, width);
		default -> transform.setTransform(0, -1, 1, 0, 0, width);
		}
		BufferedImage target = new BufferedImage(swaps ? height : width, swaps ? width : height, imageType(image));
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.drawImage(image, transform, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private static Image encode(BufferedImage image) throws IOException {
		boolean transparent = image.getColorModel().hasAlpha();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, transparent ? "png" : "jpg", out)) {
			throw new IOException("No image writer");
		}
		return new Image(out.toByteArray(), transparent ? PNG : JPEG);
	}

	private static int imageType(BufferedImage image) {
		return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
	}

	private static boolean isWebp(byte[] bytes) {
		return bytes.length >= 12
				&& new String(Arrays.copyOfRange(bytes, 0, 4), StandardCharsets.US_ASCII).equals("RIFF")
				&& new String(Arrays.copyOfRange(bytes, 8, 12), StandardCharsets.US_ASCII).equals("WEBP");
	}

	/**
	 * The EXIF orientation of a JPEG (1 when it has none or it cannot be read). Phones store a portrait photo
	 * sideways and record the turn here; browsers apply it, but a re-encoded copy would lose it.
	 */
	static int jpegOrientation(byte[] jpeg) {
		if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
			return 1;
		}
		int position = 2;
		while (position + 4 <= jpeg.length) {
			if ((jpeg[position] & 0xFF) != 0xFF) {
				return 1;
			}
			int marker = jpeg[position + 1] & 0xFF;
			int length = unsigned16(jpeg, position + 2, true);
			// Start of scan: the image data follows, and EXIF always comes before it.
			if (marker == 0xDA || length < 2) {
				return 1;
			}
			int start = position + 4;
			int end = position + 2 + length;
			if (end > jpeg.length) {
				return 1;
			}
			if (marker == 0xE1 && end - start >= 6
					&& new String(jpeg, start, 6, StandardCharsets.US_ASCII).equals("Exif\0\0")) {
				return exifOrientation(jpeg, start + 6, end);
			}
			position = end;
		}
		return 1;
	}

	private static int exifOrientation(byte[] bytes, int tiff, int end) {
		if (tiff + 8 > end) {
			return 1;
		}
		boolean bigEndian;
		if (bytes[tiff] == 'M' && bytes[tiff + 1] == 'M') {
			bigEndian = true;
		} else if (bytes[tiff] == 'I' && bytes[tiff + 1] == 'I') {
			bigEndian = false;
		} else {
			return 1;
		}
		long ifdOffset = unsigned32(bytes, tiff + 4, bigEndian);
		if (ifdOffset < 8 || tiff + ifdOffset + 2 > end) {
			return 1;
		}
		int directory = tiff + (int) ifdOffset;
		int entries = unsigned16(bytes, directory, bigEndian);
		for (int index = 0; index < entries; index++) {
			int entry = directory + 2 + index * 12;
			if (entry + 12 > end) {
				return 1;
			}
			if (unsigned16(bytes, entry, bigEndian) == EXIF_ORIENTATION_TAG) {
				int value = unsigned16(bytes, entry + 8, bigEndian);
				return value >= 1 && value <= 8 ? value : 1;
			}
		}
		return 1;
	}

	private static int unsigned16(byte[] bytes, int offset, boolean bigEndian) {
		int first = bytes[offset] & 0xFF;
		int second = bytes[offset + 1] & 0xFF;
		return bigEndian ? (first << 8) | second : (second << 8) | first;
	}

	private static long unsigned32(byte[] bytes, int offset, boolean bigEndian) {
		long high = unsigned16(bytes, offset, bigEndian);
		long low = unsigned16(bytes, offset + 2, bigEndian);
		return bigEndian ? (high << 16) | low : (low << 16) | high;
	}
}
