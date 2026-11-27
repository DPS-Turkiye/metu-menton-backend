package com.dps.teknokent.util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A file the backend hands out for saving, or a photo it hands out for showing. */
public final class Downloads {

	private static final Duration AVATAR_MAX_AGE = Duration.ofMinutes(10);

	private Downloads() {
	}

	/**
	 * Always an attachment with nosniff, so an uploaded file is never rendered in the app's origin, and never cached,
	 * since every download is personal. The name is sent UTF-8 encoded: program and file names are often Turkish.
	 */
	public static ResponseEntity<byte[]> attachment(byte[] body, String filename, String contentType) {
		return ResponseEntity.ok()
				.contentType(mediaType(contentType))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(filename, StandardCharsets.UTF_8)
						.build()
						.toString())
				.header("X-Content-Type-Options", "nosniff")
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(body);
	}

	/**
	 * A profile photo drawn in an {@code <img>}, so inline rather than an attachment. Only an image type the caller
	 * produced or checked may come here; nosniff and a sandbox policy keep the browser from treating it as anything
	 * else. Cached privately for a few minutes, since a list of people asks for each photo again on every page.
	 */
	public static ResponseEntity<byte[]> avatar(byte[] body, String imageType) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(imageType))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "default-src 'none'; sandbox")
				.cacheControl(CacheControl.maxAge(AVATAR_MAX_AGE).cachePrivate())
				.body(body);
	}

	// An uploaded file's type comes from the uploader's browser; a malformed one must not turn the download into a 500.
	private static MediaType mediaType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		try {
			return MediaType.parseMediaType(contentType);
		} catch (InvalidMediaTypeException malformed) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}
}
