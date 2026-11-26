package com.dps.teknokent.dto.requests;

/**
 * For free text students read: plain text only. A tag-like {@code <a}, {@code </} or {@code <!} is refused, while a
 * lone {@code <} ("1 < 2") is fine. The screens render it as text anyway; this keeps markup out of the data.
 */
public final class PlainText {

	public static final String PATTERN = "(?s)^(?!.*<[A-Za-z/!?]).*$";
	public static final String MESSAGE = "Use plain text, without HTML";

	private PlainText() {
	}
}
