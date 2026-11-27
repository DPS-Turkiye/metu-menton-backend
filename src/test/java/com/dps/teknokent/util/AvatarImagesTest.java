package com.dps.teknokent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AvatarImagesTest {

	private static final int RED = 0xFF0000;
	private static final int BLUE = 0x0000FF;

	@Test
	void shrinksALargeJpegToTheAvatarSizeKeepingItsShape() throws IOException {
		AvatarImages.Image avatar = AvatarImages.thumbnail(image(2000, 1000, "jpg"), "image/jpeg").orElseThrow();

		assertThat(avatar.contentType()).isEqualTo(AvatarImages.JPEG);
		assertThat(size(avatar)).containsExactly(AvatarImages.MAX_SIDE, AvatarImages.MAX_SIDE / 2);
	}

	@Test
	void keepsATransparentPngAPng() throws IOException {
		AvatarImages.Image avatar = AvatarImages.thumbnail(image(600, 900, "png"), "image/png").orElseThrow();

		assertThat(avatar.contentType()).isEqualTo(AvatarImages.PNG);
		assertThat(size(avatar)).containsExactly(171, AvatarImages.MAX_SIDE);
	}

	@Test
	void drawsASmallPhotoAfreshAtItsOwnSize() throws IOException {
		byte[] small = image(120, 120, "png");

		AvatarImages.Image avatar = AvatarImages.thumbnail(small, "IMAGE/PNG").orElseThrow();

		assertThat(avatar.bytes()).isNotSameAs(small);
		assertThat(avatar.contentType()).isEqualTo(AvatarImages.PNG);
		assertThat(size(avatar)).containsExactly(120, 120);
	}

	@Test
	void leavesAPhonesMetadataBehind() throws IOException {
		byte[] withExif = withExifOrientation(image(40, 40, "jpg"), 1, true);

		AvatarImages.Image avatar = AvatarImages.thumbnail(withExif, "image/jpeg").orElseThrow();

		assertThat(new String(avatar.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	@Test
	void sendsNoWebpTooLargeForAListOfAvatars() {
		byte[] large = Arrays.copyOf("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII),
				AvatarImages.MAX_WEBP_BYTES + 1);

		assertThat(AvatarImages.thumbnail(large, "image/webp")).isEmpty();
	}

	@Test
	void sendsAWebpAsUploadedSinceTheJdkCannotReadIt() {
		byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

		AvatarImages.Image avatar = AvatarImages.thumbnail(webp, "image/webp").orElseThrow();

		assertThat(avatar.bytes()).isSameAs(webp);
		assertThat(avatar.contentType()).isEqualTo(AvatarImages.WEBP);
	}

	@Test
	void refusesAFileThatOnlyClaimsToBeAWebp() {
		assertThat(AvatarImages.thumbnail("<svg onload=x>".getBytes(StandardCharsets.UTF_8), "image/webp"))
				.isEmpty();
	}

	@Test
	void refusesAnythingButPngJpegAndWebp() throws IOException {
		assertThat(AvatarImages.thumbnail(image(10, 10, "png"), "image/svg+xml")).isEmpty();
		assertThat(AvatarImages.thumbnail(image(10, 10, "png"), null)).isEmpty();
	}

	@Test
	void refusesBytesThatAreNoPicture() {
		assertThat(AvatarImages.thumbnail(new byte[] { 1, 2, 3, 4 }, "image/png")).isEmpty();
	}

	@Test
	void refusesAPictureThatClaimsAHugeCanvas() throws IOException {
		byte[] png = image(10, 10, "png");
		// IHDR's width and height follow the 8-byte signature, the chunk length and its type.
		writeInt(png, 16, 50_000);
		writeInt(png, 20, 50_000);

		assertThat(AvatarImages.thumbnail(png, "image/png")).isEmpty();
	}

	@Test
	void turnsAPhoneJpegUprightFromItsExifOrientation() throws IOException {
		byte[] sideways = withExifOrientation(image(400, 200, "jpg"), 6, true);

		AvatarImages.Image avatar = AvatarImages.thumbnail(sideways, "image/jpeg").orElseThrow();

		assertThat(size(avatar)).containsExactly(AvatarImages.MAX_SIDE / 2, AvatarImages.MAX_SIDE);
	}

	@Test
	void turnsASmallJpegUprightToo() throws IOException {
		byte[] sideways = withExifOrientation(image(60, 30, "jpg"), 8, false);

		AvatarImages.Image avatar = AvatarImages.thumbnail(sideways, "image/jpeg").orElseThrow();

		assertThat(size(avatar)).containsExactly(30, 60);
	}

	@Test
	void readsTheOrientationInEitherByteOrder() throws IOException {
		byte[] jpeg = image(20, 20, "jpg");

		assertThat(AvatarImages.jpegOrientation(withExifOrientation(jpeg, 3, true))).isEqualTo(3);
		assertThat(AvatarImages.jpegOrientation(withExifOrientation(jpeg, 5, false))).isEqualTo(5);
	}

	@Test
	void anUnreadableOrMissingOrientationIsUpright() throws IOException {
		byte[] jpeg = image(20, 20, "jpg");

		assertThat(AvatarImages.jpegOrientation(jpeg)).isEqualTo(1);
		assertThat(AvatarImages.jpegOrientation(new byte[] { 1, 2 })).isEqualTo(1);
		assertThat(AvatarImages.jpegOrientation(withExifOrientation(jpeg, 42, true))).isEqualTo(1);
		byte[] truncated = withExifOrientation(jpeg, 6, true);
		assertThat(AvatarImages.jpegOrientation(Arrays.copyOf(truncated, 20))).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({
			// orientation, where the stored top-left pixel lands, and the stored top-right one (x, y)
			"1, 0, 0, 1, 0",
			"2, 1, 0, 0, 0",
			"3, 1, 2, 0, 2",
			"4, 0, 2, 1, 2",
			"5, 0, 0, 0, 1",
			"6, 2, 0, 2, 1",
			"7, 2, 1, 2, 0",
			"8, 0, 1, 0, 0",
	})
	void turnsEachExifOrientationTheWayTheCameraMeantIt(int orientation, int topLeftX, int topLeftY, int topRightX,
			int topRightY) {
		// Two pixels wide and three high, with its two top corners marked.
		BufferedImage stored = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
		stored.setRGB(0, 0, RED);
		stored.setRGB(1, 0, BLUE);

		BufferedImage shown = AvatarImages.upright(stored, orientation);

		assertThat(shown.getRGB(topLeftX, topLeftY) & 0xFFFFFF).isEqualTo(RED);
		assertThat(shown.getRGB(topRightX, topRightY) & 0xFFFFFF).isEqualTo(BLUE);
	}

	private static byte[] image(int width, int height, String format) throws IOException {
		int type = format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage image = new BufferedImage(width, height, type);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static int[] size(AvatarImages.Image avatar) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(avatar.bytes()));
		return new int[] { image.getWidth(), image.getHeight() };
	}

	/** The JPEG with an APP1 EXIF segment right after its start marker, holding only an orientation. */
	private static byte[] withExifOrientation(byte[] jpeg, int orientation, boolean bigEndian) {
		byte[] tiff = new byte[26];
		tiff[0] = (byte) (bigEndian ? 'M' : 'I');
		tiff[1] = tiff[0];
		put16(tiff, 2, 42, bigEndian);
		put32(tiff, 4, 8, bigEndian);
		put16(tiff, 8, 1, bigEndian);
		put16(tiff, 10, 0x0112, bigEndian);
		put16(tiff, 12, 3, bigEndian);
		put32(tiff, 14, 1, bigEndian);
		put16(tiff, 18, orientation, bigEndian);
		byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
		int length = 2 + header.length + tiff.length;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		out.write(0xFF);
		out.write(0xE1);
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.write(header, 0, header.length);
		out.write(tiff, 0, tiff.length);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	private static void put16(byte[] bytes, int offset, int value, boolean bigEndian) {
		bytes[offset + (bigEndian ? 0 : 1)] = (byte) (value >> 8);
		bytes[offset + (bigEndian ? 1 : 0)] = (byte) value;
	}

	private static void put32(byte[] bytes, int offset, int value, boolean bigEndian) {
		put16(bytes, offset + (bigEndian ? 0 : 2), value >>> 16, bigEndian);
		put16(bytes, offset + (bigEndian ? 2 : 0), value & 0xFFFF, bigEndian);
	}

	private static void writeInt(byte[] bytes, int offset, int value) {
		bytes[offset] = (byte) (value >>> 24);
		bytes[offset + 1] = (byte) (value >>> 16);
		bytes[offset + 2] = (byte) (value >>> 8);
		bytes[offset + 3] = (byte) value;
	}
}
