package com.dps.teknokent.service;

/** {@code APP_MAIL_TRANSPORT=off}: mail counts as not configured, so callers skip it as they do without a sender. */
public class DisabledMailTransport implements MailTransport {

	@Override
	public boolean send(String from, String to, String subject, String body) {
		return false;
	}

	@Override
	public boolean readyWith(String from) {
		return false;
	}
}
