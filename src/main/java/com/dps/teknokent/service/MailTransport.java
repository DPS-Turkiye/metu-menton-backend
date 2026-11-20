package com.dps.teknokent.service;

/**
 * One way of getting an e-mail out (SES, SMTP, the log, or nowhere), chosen once at start-up by
 * {@code APP_MAIL_TRANSPORT}. {@link MailService} is what the rest of the code calls.
 */
public interface MailTransport {

	/** Sends one plain-text e-mail and says whether it was accepted. A failure is logged here, not thrown. */
	boolean send(String from, String to, String subject, String body);

	/** Whether mail can go out with this sender address. SES and SMTP need one. */
	default boolean readyWith(String from) {
		return !from.isEmpty();
	}
}
