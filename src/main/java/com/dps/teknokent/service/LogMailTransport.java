package com.dps.teknokent.service;

import com.dps.teknokent.util.EmailDomains;

import lombok.extern.slf4j.Slf4j;

/** For development: sends nothing and logs that a mail would have gone out, never its text or the full address. */
@Slf4j
public class LogMailTransport implements MailTransport {

	@Override
	public boolean send(String from, String to, String subject, String body) {
		log.info("E-mail not sent (APP_MAIL_TRANSPORT=log): \"{}\" to an address at {}", subject,
				EmailDomains.domainOf(to));
		return true;
	}

	@Override
	public boolean readyWith(String from) {
		return true;
	}
}
