package com.dps.teknokent.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.dps.teknokent.config.SmtpProperties;
import com.dps.teknokent.domain.SmtpStartTls;
import com.dps.teknokent.util.EmailDomains;

import jakarta.mail.SendFailedException;
import lombok.extern.slf4j.Slf4j;

/** An SMTP server, such as ODTÜ's own relay. Every send opens its own connection and closes it. */
@Slf4j
public class SmtpMailTransport implements MailTransport {

	/**
	 * For connecting, and for each answer and each write after that. A server that stops answering fails the send
	 * within this long, as the SES client's 10 s does.
	 */
	static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final Pattern ADDRESS = Pattern.compile("[^\\s<>\"'(),;:\\[\\]]*@[^\\s<>\"'(),;:\\[\\]]*");
	private static final Pattern REPLY_CODE = Pattern.compile("^\\s*(\\d{3}(?:[ -]\\d\\.\\d{1,3}\\.\\d{1,3})?)");
	private static final Pattern BREAKS_AND_CONTROLS = Pattern.compile("[\\s\\p{Cntrl}]+");
	private static final int LONGEST_REASON = 300;

	private final JavaMailSender sender;

	public SmtpMailTransport(SmtpProperties smtp) {
		this(sender(smtp));
	}

	SmtpMailTransport(JavaMailSender sender) {
		this.sender = sender;
	}

	@Override
	public boolean send(String from, String to, String subject, String body) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to.trim());
		// A subject can carry a person's name; a line break in it would start a header of its own.
		message.setSubject(BREAKS_AND_CONTROLS.matcher(subject).replaceAll(" "));
		message.setText(body);
		try {
			sender.send(message);
			return true;
		} catch (RuntimeException failed) {
			// Whatever went wrong, an e-mail never fails the request that caused it. No address in the log (KVKK).
			log.warn("E-mail to an address at {} could not be sent over SMTP: {}", EmailDomains.domainOf(to),
					reason(failed));
			return false;
		}
	}

	static JavaMailSenderImpl sender(SmtpProperties smtp) {
		JavaMailSenderImpl mail = new JavaMailSenderImpl();
		mail.setHost(smtp.host());
		mail.setPort(smtp.portNumber());
		mail.setDefaultEncoding(StandardCharsets.UTF_8.name());
		Properties session = mail.getJavaMailProperties();
		String timeout = String.valueOf(TIMEOUT.toMillis());
		session.setProperty("mail.smtp.connectiontimeout", timeout);
		session.setProperty("mail.smtp.timeout", timeout);
		session.setProperty("mail.smtp.writetimeout", timeout);
		SmtpStartTls startTls = smtp.startTls();
		session.setProperty("mail.smtp.starttls.enable", String.valueOf(startTls != SmtpStartTls.OFF));
		session.setProperty("mail.smtp.starttls.required", String.valueOf(startTls == SmtpStartTls.REQUIRED));
		// Encryption alone would still talk to anyone who answers on that address.
		session.setProperty("mail.smtp.ssl.checkserveridentity", "true");
		if (smtp.hasLogin()) {
			mail.setUsername(smtp.username());
			mail.setPassword(smtp.password());
			session.setProperty("mail.smtp.auth", "true");
		}
		return mail;
	}

	/**
	 * What failed, safe to log. A refused message keeps only the server's reply code: the rest of that answer
	 * repeats the recipient ("550 5.1.1 <who@where>: user unknown"). Anything else (no connection, no STARTTLS, a
	 * certificate, a login) keeps its text, with whatever looks like an address taken out.
	 */
	static String reason(RuntimeException failed) {
		Throwable first = failed;
		if (failed instanceof MailSendException unsent && unsent.getMessageExceptions().length > 0) {
			first = unsent.getMessageExceptions()[0];
		}
		Throwable root = NestedExceptionUtils.getMostSpecificCause(first);
		String name = root.getClass().getSimpleName();
		String message = root.getMessage() == null ? "" : root.getMessage();
		if (root instanceof SendFailedException) {
			Matcher code = REPLY_CODE.matcher(message);
			return code.find() ? name + ": " + code.group(1) : name;
		}
		String capped = message.length() > LONGEST_REASON ? message.substring(0, LONGEST_REASON) : message;
		String safe = BREAKS_AND_CONTROLS.matcher(ADDRESS.matcher(capped).replaceAll("<address>")).replaceAll(" ")
				.strip();
		return safe.isEmpty() ? name : name + ": " + safe;
	}
}
