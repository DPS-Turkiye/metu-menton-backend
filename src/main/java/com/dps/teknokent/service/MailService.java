package com.dps.teknokent.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MailService {

	private final MailTransport transport;
	private final String fromAddress;
	private final String publicUrl;
	private final Executor afterCommitExecutor;

	public MailService(
			MailTransport transport,
			@Value("${app.mail.from:}") String fromAddress,
			@Value("${app.mail.public-url:https://menton.com.tr}") String publicUrl,
			@Qualifier("mailExecutor") Executor afterCommitExecutor) {
		this.transport = transport;
		this.afterCommitExecutor = afterCommitExecutor;
		this.fromAddress = fromAddress.trim();
		this.publicUrl = publicUrl.trim().replaceAll("/+$", "");
	}

	public String link(String path) {
		return publicUrl + path;
	}

	public boolean isConfigured() {
		return transport.readyWith(fromAddress);
	}

	/** Sends right away and says whether the mail was accepted; a bounce reported later is not seen here. */
	public boolean sendNow(String to, String subject, String body) {
		return isConfigured() && to != null && !to.isBlank() && send(to, subject, body);
	}

	public void sendAfterCommit(String to, String subject, String body) {
		if (!isConfigured() || to == null || to.isBlank()) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			send(to, subject, body);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				// afterCommit runs before the transaction hands its connection back, so sending here would hold
				// that connection for the whole round trip to the mail server.
				try {
					afterCommitExecutor.execute(() -> {
						try {
							send(to, subject, body);
						} catch (RuntimeException error) {
							log.warn("Email to {} failed: {}", to, error.getMessage());
						}
					});
				} catch (RejectedExecutionException shuttingDown) {
					// Only while shutting down; this inline send does hold the connection.
					send(to, subject, body);
				}
			}
		});
	}

	private boolean send(String to, String subject, String body) {
		return transport.send(fromAddress, to, subject, body);
	}
}
