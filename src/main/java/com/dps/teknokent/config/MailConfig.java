package com.dps.teknokent.config;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.dps.teknokent.domain.MailTransportType;
import com.dps.teknokent.service.DisabledMailTransport;
import com.dps.teknokent.service.LogMailTransport;
import com.dps.teknokent.service.MailTransport;
import com.dps.teknokent.service.SesMailTransport;
import com.dps.teknokent.service.SmtpMailTransport;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Slf4j
@Configuration
@EnableConfigurationProperties(SmtpProperties.class)
public class MailConfig {

	/** Built only when SES is the transport: a server that sends over SMTP has no AWS region to build it with. */
	@Bean
	@Lazy
	SesV2Client sesV2Client(
			@Value("${app.mail.region:eu-central-1}") String region,
			@Value("${app.mail.access-key-id:}") String accessKeyId,
			@Value("${app.mail.secret-access-key:}") String secretAccessKey) {
		// The SDK default has no cap on a whole call (retries included); an email is small, 10 s is plenty.
		var builder = SesV2Client.builder()
				.region(Region.of(region))
				.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10)));
		if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
		}
		return builder.build();
	}

	@Bean
	MailTransport mailTransport(
			@Value("${app.mail.transport:}") String transport,
			@Value("${app.mail.from:}") String from,
			SmtpProperties smtp,
			ObjectProvider<SesV2Client> sesClient) {
		return switch (MailTransportType.parse(transport)) {
		case SES -> new SesMailTransport(sesClient.getObject());
		case SMTP -> smtpTransport(smtp, from.trim());
		case LOG -> {
			log.info("E-mail is only logged (APP_MAIL_TRANSPORT=log): nothing is sent.");
			yield new LogMailTransport();
		}
		case OFF -> {
			log.info("E-mail is off (APP_MAIL_TRANSPORT=off): nothing is sent.");
			yield new DisabledMailTransport();
		}
		};
	}

	/**
	 * Runs after-commit emails off the request thread. Two at a time keeps a burst under the SES send rate;
	 * closing it on shutdown lets queued emails finish.
	 */
	@Bean(destroyMethod = "close")
	ExecutorService mailExecutor() {
		return Executors.newFixedThreadPool(2, Thread.ofVirtual().name("mail-", 0).factory());
	}

	/**
	 * With SES an empty sender quietly means "no e-mail". A deployment that asks for SMTP means to send, so a
	 * missing server or sender stops the start-up instead of dropping every mail in silence.
	 */
	private static MailTransport smtpTransport(SmtpProperties smtp, String from) {
		smtp.requireUsable();
		requireSender(from);
		if (!smtp.alwaysEncrypted()) {
			if (smtp.hasLogin()) {
				// "optional" too: whoever sits between the two servers can hide the STARTTLS offer.
				throw new IllegalStateException("SMTP_USERNAME and SMTP_PASSWORD need SMTP_STARTTLS=required (or"
						+ " SMTP_TLS=implicit): without it the login can cross the network unencrypted.");
			}
			log.warn("SMTP_STARTTLS={}: e-mails can cross the network to the mail server unencrypted.",
					smtp.startTls().name().toLowerCase(Locale.ROOT));
		}
		log.info("E-mail goes over SMTP: {}:{}, {}, {}.", smtp.host(), smtp.portNumber(), smtp.encryption(),
				smtp.hasLogin() ? "with a login" : "without a login");
		return new SmtpMailTransport(smtp);
	}

	private static void requireSender(String from) {
		if (from.isEmpty()) {
			throw new IllegalStateException(
					"APP_MAIL_TRANSPORT=smtp needs MAIL_FROM, the sender address the mail server accepts.");
		}
		try {
			new InternetAddress(from).validate();
		} catch (AddressException invalid) {
			throw new IllegalStateException("MAIL_FROM is not a valid e-mail address.", invalid);
		}
	}
}
