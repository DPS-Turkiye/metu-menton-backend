package com.dps.teknokent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import software.amazon.awssdk.auth.credentials.AwsCredentials;

class S3PropertiesTest {

	private static S3Properties withEndpoint(String endpoint, String publicEndpoint) {
		return new S3Properties("us-east-1", "menton-odtu", endpoint, publicEndpoint, "key", "secret", "eu-central-1",
				"iam-key", "iam-secret");
	}

	private static String accessKeyOf(S3Properties properties) {
		return properties.credentials().orElseThrow().resolveCredentials().accessKeyId();
	}

	@Test
	void treatsUnsetValuesAsEmpty() {
		S3Properties properties = new S3Properties(null, null, null, null, null, null, null, null, null);

		assertThat(properties.hasBucket()).isFalse();
		assertThat(properties.effectiveRegion()).isEmpty();
		assertThat(properties.endpointUri()).isEmpty();
		assertThat(properties.presignEndpointUri()).isEmpty();
		assertThat(properties.credentials()).isEmpty();
	}

	@Test
	void fallsBackToTheAwsRegionWhenTheStoreHasNone() {
		assertThat(new S3Properties(" ", "b", "", "", "", "", "eu-central-1", "", "").effectiveRegion())
				.isEqualTo("eu-central-1");
		assertThat(new S3Properties("us-east-1", "b", "", "", "", "", "eu-central-1", "", "").effectiveRegion())
				.isEqualTo("us-east-1");
	}

	@Test
	void awsS3UsesTheAwsKeysUnlessItHasItsOwn() {
		assertThat(accessKeyOf(new S3Properties("eu-central-1", "b", "", "", "", "", "eu-central-1", "iam-key",
				"iam-secret"))).isEqualTo("iam-key");
		assertThat(accessKeyOf(new S3Properties("eu-central-1", "b", "", "", "s3-key", "s3-secret", "eu-central-1",
				"iam-key", "iam-secret"))).isEqualTo("s3-key");
		assertThat(new S3Properties("eu-central-1", "b", "", "", "", "", "eu-central-1", "iam-key", "")
				.credentials()).isEmpty();
	}

	@Test
	void anS3CompatibleStoreNeverGetsTheAwsKeys() {
		S3Properties withoutOwnKeys = new S3Properties("us-east-1", "menton-odtu", "http://minio:9000", "", "", "",
				"eu-central-1", "iam-key", "iam-secret");

		assertThat(withoutOwnKeys.credentials()).isEmpty();
		assertThat(accessKeyOf(withEndpoint("http://minio:9000", ""))).isEqualTo("key");
	}

	@Test
	void signsPresignedLinksForThePublicAddressWhenThereIsOne() {
		assertThat(withEndpoint("http://minio:9000", "https://files.example.org").presignEndpointUri())
				.contains(URI.create("https://files.example.org"));
		assertThat(withEndpoint("http://minio:9000", " ").presignEndpointUri())
				.contains(URI.create("http://minio:9000"));
	}

	@Test
	void refusesAPublicAddressWithoutAStoreOfItsOwn() {
		assertThatThrownBy(() -> withEndpoint("", "https://files.example.org").presignEndpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AWS_S3_PUBLIC_ENDPOINT is set without AWS_S3_ENDPOINT");
	}

	@Test
	void refusesAnEndpointThatIsNotAPlainHttpUrlWithoutEchoingIt() {
		assertThatThrownBy(() -> withEndpoint("minio:9000", "").endpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("AWS_S3_ENDPOINT must be an http(s) URL with a host, such as http://minio:9000.");
		assertThatThrownBy(() -> withEndpoint("ftp://minio:9000", "").endpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must be an http(s) URL");
		assertThatThrownBy(() -> withEndpoint("http://bad host", "").endpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("AWS_S3_ENDPOINT is not a valid URL.")
				.hasNoCause();
		assertThatThrownBy(() -> withEndpoint("http://minio:9000?x=1", "").endpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must not carry credentials, a query or a fragment");
		assertThatThrownBy(() -> withEndpoint("http://minio:9000", "localhost:9000").presignEndpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AWS_S3_PUBLIC_ENDPOINT must be an http(s) URL");
	}

	@Test
	void refusesCredentialsInTheUrlAndNeverPrintsThem() {
		assertThatThrownBy(() -> withEndpoint("http://admin:hunter2@minio:9000", "").endpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("hunter2")
				.hasMessageContaining("must not carry credentials");
		assertThatThrownBy(() -> withEndpoint("admin:hunter2@minio:9000", "").endpointUri())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("hunter2");
	}

	@Test
	void knowsWhenFilesWouldLandAtAmazon() {
		assertThat(withEndpoint("", "").pointsAtAws()).isTrue();
		assertThat(withEndpoint("https://s3.eu-central-1.amazonaws.com", "").pointsAtAws()).isTrue();
		assertThat(withEndpoint("https://S3.AMAZONAWS.COM", "").pointsAtAws()).isTrue();
		assertThat(withEndpoint("https://s3.eu-central-1.amazonaws.com.", "").pointsAtAws()).isTrue();
		assertThat(withEndpoint("https://s3.cn-north-1.amazonaws.com.cn", "").pointsAtAws()).isTrue();
		assertThat(withEndpoint("https://s3.dualstack.eu-central-1.api.aws", "").pointsAtAws()).isTrue();
		assertThat(withEndpoint("http://minio:9000", "").pointsAtAws()).isFalse();
		assertThat(withEndpoint("https://notamazonaws.com", "").pointsAtAws()).isFalse();
	}

	@Test
	void bindsFromTheEnvironmentAndKeepsTheMailKeysApart() throws IOException {
		Map<String, Object> variables = new HashMap<>();
		variables.put("AWS_REGION", "eu-central-1");
		variables.put("AWS_ACCESS_KEY_ID", "iam-key");
		variables.put("AWS_SECRET_ACCESS_KEY", "iam-secret");
		variables.put("AWS_S3_BUCKET", "menton-odtu");
		variables.put("AWS_S3_ENDPOINT", "http://minio:9000");
		variables.put("AWS_S3_REGION", "");
		variables.put("AWS_S3_ACCESS_KEY_ID", "minio-key");
		variables.put("AWS_S3_SECRET_ACCESS_KEY", "minio-secret");
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
		// The main file, not the test one that shadows it on the classpath.
		new YamlPropertySourceLoader()
				.load("application", new FileSystemResource("src/main/resources/application.yml"))
				.forEach(environment.getPropertySources()::addLast);

		S3Properties s3 = Binder.get(environment).bind("app.s3", S3Properties.class).get();
		AwsCredentials keys = s3.credentials().orElseThrow().resolveCredentials();

		assertThat(s3.effectiveRegion()).isEqualTo("eu-central-1");
		assertThat(s3.endpointUri()).contains(URI.create("http://minio:9000"));
		assertThat(keys.accessKeyId()).isEqualTo("minio-key");
		assertThat(keys.secretAccessKey()).isEqualTo("minio-secret");
		assertThat(environment.getProperty("app.mail.access-key-id")).isEqualTo("iam-key");
		assertThat(environment.getProperty("app.mail.secret-access-key")).isEqualTo("iam-secret");
	}
}
