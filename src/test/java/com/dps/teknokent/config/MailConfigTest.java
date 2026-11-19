package com.dps.teknokent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import com.dps.teknokent.domain.SmtpStartTls;
import com.dps.teknokent.domain.SmtpTls;
import com.dps.teknokent.service.DisabledMailTransport;
import com.dps.teknokent.service.LogMailTransport;
import com.dps.teknokent.service.MailTransport;
import com.dps.teknokent.service.SesMailTransport;
import com.dps.teknokent.service.SmtpMailTransport;

@ExtendWith(OutputCaptureExtension.class)
class MailConfigTest {

	private static final String SMTP = "app.mail.transport=smtp";
	private static final String HOST = "app.mail.smtp.host=smtp.example.test";
	private static final String FROM = "app.mail.from=noreply@example.test";

	private final ApplicationContextRunner context = new ApplicationContextRunner()
			.withUserConfiguration(MailConfig.class);

	// The deployments that existed before the setting did must keep sending through SES.
	@Test
	void sesIsTheTransportUnlessAnotherIsChosen() {
		context.run(started -> assertThat(started.getBean(MailTransport.class)).isInstanceOf(SesMailTransport.class));
		context.withPropertyValues("app.mail.transport=")
				.run(started -> assertThat(started.getBean(MailTransport.class))
						.isInstanceOf(SesMailTransport.class));
		context.withPropertyValues("app.mail.transport= SES ")
				.run(started -> assertThat(started.getBean(MailTransport.class))
						.isInstanceOf(SesMailTransport.class));
	}

	// A blank AWS region stops the SES client from being built; a server on SMTP has none and must not need one.
	@Test
	void smtpStartsWithoutAnAwsRegion(CapturedOutput output) {
		context.withPropertyValues(SMTP, HOST, FROM, "app.mail.region=")
				.run(started -> assertThat(started.getBean(MailTransport.class))
						.isInstanceOf(SmtpMailTransport.class));

		assertThat(output).contains("E-mail goes over SMTP: smtp.example.test:587, STARTTLS required,"
				+ " without a login.");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"app.mail.smtp.host= | SMTP_HOST",
			"app.mail.smtp.host=smtp://smtp.example.test | SMTP_HOST must be",
			"app.mail.from= | MAIL_FROM",
			"app.mail.from=not-an-address | MAIL_FROM is not a valid",
			"app.mail.smtp.port=smtp | SMTP_PORT",
			"app.mail.smtp.port=0 | SMTP_PORT",
			"app.mail.smtp.port=70000 | SMTP_PORT",
			"app.mail.smtp.starttls=tls | SMTP_STARTTLS",
			"app.mail.smtp.tls=ssl | SMTP_TLS",
			"app.mail.smtp.username=menton-relay | SMTP_PASSWORD",
			"app.mail.smtp.password=relay-secret-1 | SMTP_USERNAME",
			"app.mail.smtp.username=menton-relay,app.mail.smtp.password=relay-secret-1,app.mail.smtp.starttls=optional"
					+ " | need SMTP_STARTTLS=required",
			"app.mail.smtp.username=menton-relay,app.mail.smtp.password=relay-secret-1,app.mail.smtp.starttls=off"
					+ " | need SMTP_STARTTLS=required" })
	void smtpStopsTheStartUpOnASettingThatCouldNeverSend(String setting, String named, CapturedOutput output) {
		context.withPropertyValues(SMTP, HOST, FROM).withPropertyValues(setting.split(","))
				.run(started -> assertThat(started).hasFailed()
						.getFailure().hasStackTraceContaining(named));

		assertThat(output).doesNotContain("relay-secret-1");
	}

	@Test
	void anUnknownTransportStopsTheStartUp() {
		context.withPropertyValues("app.mail.transport=sendmail")
				.run(started -> assertThat(started).hasFailed()
						.getFailure().hasStackTraceContaining("APP_MAIL_TRANSPORT) must be ses, smtp, log or off"));
	}

	@Test
	void aLoginOverRequiredStartTlsStartsAndThePasswordIsNeverLogged(CapturedOutput output) {
		context.withPropertyValues(SMTP, HOST, FROM, "app.mail.smtp.username=menton-relay",
				"app.mail.smtp.password=relay-secret-1")
				.run(started -> {
					assertThat(started).hasNotFailed();
					assertThat(started.getBean(SmtpProperties.class).toString())
							.contains("login=set")
							.doesNotContain("relay-secret-1");
				});

		assertThat(output).contains("STARTTLS required, with a login").doesNotContain("relay-secret-1")
				.doesNotContain("unencrypted");
	}

	// Over TLS from the first byte SMTP_STARTTLS plays no part, so "off" there exposes nothing.
	@Test
	void aLoginOverImplicitTlsStartsOnPort465(CapturedOutput output) {
		context.withPropertyValues(SMTP, HOST, FROM, "app.mail.smtp.username=menton-relay",
				"app.mail.smtp.password=relay-secret-1", "app.mail.smtp.tls=implicit", "app.mail.smtp.starttls=off")
				.run(started -> assertThat(started).hasNotFailed());

		assertThat(output).contains("smtp.example.test:465, TLS from the first byte, with a login")
				.doesNotContain("unencrypted");
	}

	@Test
	void mailThatCanCrossTheNetworkUnencryptedIsWarnedAbout(CapturedOutput output) {
		context.withPropertyValues(SMTP, HOST, FROM, "app.mail.smtp.starttls=off")
				.run(started -> assertThat(started).hasNotFailed());

		assertThat(output).contains("SMTP_STARTTLS=off: e-mails can cross the network");
	}

	@Test
	void offSaysSoOnceAtStartUpAndSendsNothing(CapturedOutput output) {
		context.withPropertyValues("app.mail.transport=off", FROM)
				.run(started -> {
					MailTransport transport = started.getBean(MailTransport.class);
					assertThat(transport).isInstanceOf(DisabledMailTransport.class);
					assertThat(transport.readyWith("noreply@example.test")).isFalse();
					assertThat(transport.send("noreply@example.test", "student@example.test", "Konu", "Metin"))
							.isFalse();
				});

		assertThat(output.getOut()).containsOnlyOnce("E-mail is off (APP_MAIL_TRANSPORT=off)");
	}

	@Test
	void logOnlyLogs(CapturedOutput output) {
		context.withPropertyValues("app.mail.transport=log")
				.run(started -> assertThat(started.getBean(MailTransport.class))
						.isInstanceOf(LogMailTransport.class));

		assertThat(output).contains("E-mail is only logged (APP_MAIL_TRANSPORT=log)");
	}

	// The deployments set these environment variables; application.yml must still route each to its setting.
	@Test
	void theEnvironmentVariablesInEnvExampleReachEverySetting() throws IOException {
		StandardEnvironment environment = fromApplicationYml(Map.of(
				"APP_MAIL_TRANSPORT", "smtp",
				"MAIL_FROM", "noreply@example.test",
				"SMTP_HOST", " smtp.example.test ",
				"SMTP_PORT", "25",
				"SMTP_USERNAME", "menton-relay",
				"SMTP_PASSWORD", "relay-secret-1",
				"SMTP_TLS", "Implicit",
				"SMTP_STARTTLS", "Optional"));
		SmtpProperties smtp = Binder.get(environment).bindOrCreate("app.mail.smtp", SmtpProperties.class);

		assertThat(environment.getProperty("app.mail.transport")).isEqualTo("smtp");
		assertThat(environment.getProperty("app.mail.from")).isEqualTo("noreply@example.test");
		assertThat(smtp.host()).isEqualTo("smtp.example.test");
		assertThat(smtp.portNumber()).isEqualTo(25);
		assertThat(smtp.username()).isEqualTo("menton-relay");
		assertThat(smtp.password()).isEqualTo("relay-secret-1");
		assertThat(smtp.tlsMode()).isEqualTo(SmtpTls.IMPLICIT);
		assertThat(smtp.startTls()).isEqualTo(SmtpStartTls.OPTIONAL);
	}

	// An env var declared empty never reaches a placeholder's default, so blank has to mean the default too.
	@Test
	void withNoOrEmptyEnvironmentVariablesMailStaysOnSes() throws IOException {
		assertThat(fromApplicationYml(Map.of()).getProperty("app.mail.transport")).isEqualTo("ses");

		Map<String, Object> declaredEmpty = Map.of("SMTP_PORT", "", "SMTP_TLS", "", "SMTP_STARTTLS", "");
		SmtpProperties empty = Binder.get(fromApplicationYml(declaredEmpty))
				.bindOrCreate("app.mail.smtp", SmtpProperties.class);
		assertThat(empty.portNumber()).isEqualTo(587);
		assertThat(empty.tlsMode()).isEqualTo(SmtpTls.STARTTLS);
		assertThat(empty.startTls()).isEqualTo(SmtpStartTls.REQUIRED);
		assertThat(empty.hasLogin()).isFalse();
	}

	private static StandardEnvironment fromApplicationYml(Map<String, Object> env) throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		environment.getPropertySources().addFirst(new MapPropertySource("env", env));
		for (PropertySource<?> source : new YamlPropertySourceLoader().load("application.yml",
				new FileSystemResource("src/main/resources/application.yml"))) {
			environment.getPropertySources().addLast(source);
		}
		return environment;
	}
}
