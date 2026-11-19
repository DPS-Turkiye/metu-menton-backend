package com.dps.teknokent.service;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/** Amazon SES; a bounce reported later is not seen here. */
@Slf4j
public class SesMailTransport implements MailTransport {

	private final SesV2Client sesClient;

	public SesMailTransport(SesV2Client sesClient) {
		this.sesClient = sesClient;
	}

	@Override
	public boolean send(String from, String to, String subject, String body) {
		try {
			sesClient.sendEmail(SendEmailRequest.builder()
					.fromEmailAddress(from)
					.destination(Destination.builder().toAddresses(to.trim()).build())
					.content(EmailContent.builder()
							.simple(Message.builder()
									.subject(utf8(subject))
									.body(Body.builder().text(utf8(body)).build())
									.build())
							.build())
					.build());
			return true;
		} catch (SdkException e) {
			log.warn("Email to {} could not be sent: {}", to, e.getMessage());
			return false;
		}
	}

	private static Content utf8(String value) {
		return Content.builder().data(value).charset(StandardCharsets.UTF_8.name()).build();
	}
}
