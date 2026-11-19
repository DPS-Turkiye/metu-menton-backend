package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.dps.teknokent.config.SmtpProperties;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;

/** Against GreenMail, an in-memory SMTP server. It speaks plain SMTP only: it has no STARTTLS to offer. */
@ExtendWith(OutputCaptureExtension.class)
class SmtpMailTransportTest {

	private static final String FROM = "noreply@example.test";
	private static final String STUDENT = "student@example.test";

	private final GreenMail server = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

	@BeforeEach
	void startTheServer() {
		server.start();
	}

	@AfterEach
	void stopTheServer() {
		if (server.isRunning()) {
			server.stop();
		}
	}

	@Test
	void sendsAPlainTextMailWithTurkishLetters() throws MessagingException, IOException {
		boolean sent = transport("off", "", "").send(FROM, " " + STUDENT + " ", "Görüşme onaylandı",
				"Merhaba Çağrı, görüşmeniz onaylandı.");

		assertThat(sent).isTrue();
		assertThat(server.getReceivedMessages()).hasSize(1);
		MimeMessage received = server.getReceivedMessages()[0];
		assertThat(received.getFrom()[0]).hasToString(FROM);
		assertThat(received.getAllRecipients()[0]).hasToString(STUDENT);
		assertThat(received.getSubject()).isEqualTo("Görüşme onaylandı");
		assertThat(received.getContent().toString().strip()).isEqualTo("Merhaba Çağrı, görüşmeniz onaylandı.");
	}

	@Test
	void optionalStartTlsStillSendsToAServerThatCannotEncrypt() {
		assertThat(transport("optional", "", "").send(FROM, STUDENT, "Konu", "Metin")).isTrue();
		assertThat(server.getReceivedMessages()).hasSize(1);
	}

	@Test
	void requiredStartTlsSendsNothingToAServerThatCannotEncrypt(CapturedOutput output) {
		assertThat(transport("required", "", "").send(FROM, STUDENT, "Konu", "Metin")).isFalse();

		assertThat(server.getReceivedMessages()).isEmpty();
		assertThat(output).contains("could not be sent over SMTP").doesNotContain(STUDENT);
	}

	@Test
	void requiredStartTlsIsWhatAnEmptySettingMeans() {
		assertThat(transport("", "", "").send(FROM, STUDENT, "Konu", "Metin")).isFalse();
		assertThat(server.getReceivedMessages()).isEmpty();
	}

	// MailConfig refuses a login without required STARTTLS; here it only shows that the login is really used.
	@Test
	void logsInWithTheConfiguredAccount(CapturedOutput output) {
		server.setUser("relay@example.test", "menton-relay", "relay-secret-1");

		assertThat(transport("off", "menton-relay", "relay-secret-1").send(FROM, STUDENT, "Konu", "Metin")).isTrue();
		assertThat(transport("off", "menton-relay", "wrong-secret-2").send(FROM, STUDENT, "Konu", "Metin"))
				.isFalse();

		assertThat(server.getReceivedMessages()).hasSize(1);
		assertThat(output).doesNotContain("relay-secret-1").doesNotContain("wrong-secret-2");
	}

	@Test
	void aServerThatIsDownIsLoggedWithoutTheAddressAndBreaksNothing(CapturedOutput output) {
		SmtpMailTransport transport = transport("off", "", "");
		server.stop();

		assertThat(transport.send(FROM, STUDENT, "Konu", "Metin")).isFalse();

		assertThat(output).contains("E-mail to an address at example.test could not be sent over SMTP")
				.doesNotContain(STUDENT);
	}

	@Test
	void takesTheAddressOutOfTheServersRefusal(CapturedOutput output) {
		JavaMailSender sender = mock(JavaMailSender.class);
		doThrow(new MailSendException("550 5.1.1 <" + STUDENT + ">:\r\nRecipient address rejected"))
				.when(sender).send(any(SimpleMailMessage.class));

		assertThat(new SmtpMailTransport(sender).send(FROM, STUDENT, "Konu", "Metin")).isFalse();

		assertThat(output).contains("550 5.1.1 <<address>>: Recipient address rejected").doesNotContain(STUDENT);
	}

	@Test
	void anUnexpectedFailureIsReportedAsNotSentToo() {
		JavaMailSender sender = mock(JavaMailSender.class);
		doThrow(new IllegalStateException()).when(sender).send(any(SimpleMailMessage.class));

		assertThat(new SmtpMailTransport(sender).send(FROM, STUDENT, "Konu", "Metin")).isFalse();
	}

	@Test
	void aRefusedMessageKeepsOnlyTheServersReplyCode() {
		MailSendException unsent = new MailSendException("Failed messages", null, Map.of(new SimpleMailMessage(),
				new SendFailedException("550 5.1.1 <" + STUDENT + ">: user Deniz Kaya unknown")));

		assertThat(SmtpMailTransport.reason(unsent)).isEqualTo("SendFailedException: 550 5.1.1");
		assertThat(SmtpMailTransport.reason(new MailSendException("Failed messages", null,
				Map.of(new SimpleMailMessage(), new SendFailedException("Invalid Addresses")))))
				.isEqualTo("SendFailedException");
	}

	@Test
	void aLongAnswerIsCutShort() {
		String reason = SmtpMailTransport.reason(new IllegalStateException("x".repeat(1000)));

		assertThat(reason).hasSize("IllegalStateException: ".length() + 300);
	}

	@Test
	void aLineBreakInTheSubjectCannotStartAHeaderOfItsOwn() throws MessagingException {
		transport("off", "", "").send(FROM, STUDENT, "Görüşme\r\nBcc: other@example.test", "Metin");

		MimeMessage received = server.getReceivedMessages()[0];
		assertThat(received.getSubject()).isEqualTo("Görüşme Bcc: other@example.test");
		assertThat(received.getHeader("Bcc")).isNull();
	}

	@Test
	void everyStepOfAConversationTimesOutAfterTenSeconds() {
		Properties session = SmtpMailTransport.sender(new SmtpProperties("smtp.example.test", "", "", "", ""))
				.getJavaMailProperties();

		assertThat(SmtpMailTransport.TIMEOUT).hasSeconds(10);
		assertThat(session).containsEntry("mail.smtp.connectiontimeout", "10000")
				.containsEntry("mail.smtp.timeout", "10000")
				.containsEntry("mail.smtp.writetimeout", "10000");
	}

	@Test
	void encryptsAndChecksTheServersNameUnlessToldOtherwise() {
		JavaMailSenderImpl byDefault = SmtpMailTransport.sender(
				new SmtpProperties("smtp.example.test", "", "", "", ""));
		assertThat(byDefault.getPort()).isEqualTo(587);
		assertThat(byDefault.getDefaultEncoding()).isEqualTo("UTF-8");
		assertThat(byDefault.getJavaMailProperties()).containsEntry("mail.smtp.starttls.enable", "true")
				.containsEntry("mail.smtp.starttls.required", "true")
				.containsEntry("mail.smtp.ssl.checkserveridentity", "true")
				.doesNotContainKey("mail.smtp.auth");

		Properties optional = SmtpMailTransport.sender(
				new SmtpProperties("smtp.example.test", "25", "menton-relay", "relay-secret-1", "optional"))
				.getJavaMailProperties();
		assertThat(optional).containsEntry("mail.smtp.starttls.enable", "true")
				.containsEntry("mail.smtp.starttls.required", "false")
				.containsEntry("mail.smtp.auth", "true");

		Properties off = SmtpMailTransport.sender(new SmtpProperties("smtp.example.test", "25", "", "", "off"))
				.getJavaMailProperties();
		assertThat(off).containsEntry("mail.smtp.starttls.enable", "false")
				.containsEntry("mail.smtp.starttls.required", "false");
	}

	private SmtpMailTransport transport(String startTls, String username, String password) {
		return new SmtpMailTransport(new SmtpProperties("127.0.0.1", String.valueOf(server.getSmtp().getPort()),
				username, password, startTls));
	}
}
