package com.dps.teknokent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.dps.teknokent.domain.Edition;
import com.sun.net.httpserver.HttpServer;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3ConfigTest {

	private static final S3Properties AWS = new S3Properties("", "teknokent-bucket", "", "", "", "", "eu-central-1",
			"iam-key", "iam-secret");

	private static S3Properties store(String endpoint, String publicEndpoint) {
		return new S3Properties("us-east-1", "menton-odtu", endpoint, publicEndpoint, "menton-local",
				"menton-local-secret", "eu-central-1", "iam-key", "iam-secret");
	}

	private static URL presignGet(S3Properties properties, String key) {
		try (S3Presigner presigner = new S3Config().s3Presigner(properties)) {
			return presigner.presignGetObject(GetObjectPresignRequest.builder()
					.signatureDuration(Duration.ofMinutes(5))
					.getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
					.build())
					.url();
		}
	}

	private static URL presignPut(S3Properties properties, String key) {
		try (S3Presigner presigner = new S3Config().s3Presigner(properties)) {
			return presigner.presignPutObject(PutObjectPresignRequest.builder()
					.signatureDuration(Duration.ofMinutes(5))
					.putObjectRequest(PutObjectRequest.builder()
							.bucket(properties.bucket())
							.key(key)
							.contentType("application/json")
							.build())
					.build())
					.url();
		}
	}

	@Test
	void withoutAnEndpointTheClientsTalkToAwsAsBefore() {
		try (S3Client s3 = new S3Config().s3Client(AWS, "TEKNOKENT")) {
			assertThat(s3.serviceClientConfiguration().endpointOverride()).isEmpty();
		}

		URL url = presignGet(AWS, "documents/a.pdf");

		assertThat(url.getProtocol()).isEqualTo("https");
		assertThat(url.getHost()).isEqualTo("teknokent-bucket.s3.eu-central-1.amazonaws.com");
		assertThat(url.getPath()).isEqualTo("/documents/a.pdf");
	}

	@Test
	void withAnEndpointTheClientUsesPathStyleOnThatStoreAndSendsNoDefaultChecksums() throws IOException {
		List<String> paths = new CopyOnWriteArrayList<>();
		List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			paths.add(exchange.getRequestURI().getPath());
			headers.add(Map.copyOf(exchange.getRequestHeaders()));
			exchange.getRequestBody().readAllBytes();
			exchange.getResponseHeaders().add("ETag", "\"etag\"");
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		// A host name, not an IP literal: the SDK would fall back to path style for an IP on its own.
		String endpoint = "http://localhost:" + server.getAddress().getPort();
		try (S3Client s3 = new S3Config().s3Client(store(endpoint, ""), "ODTU")) {
			s3.putObject(
					PutObjectRequest.builder().bucket("menton-odtu").key("documents/a.json").build(),
					RequestBody.fromString("{}", StandardCharsets.UTF_8));
		} finally {
			server.stop(0);
		}

		assertThat(paths).containsExactly("/menton-odtu/documents/a.json");
		assertThat(headers.getFirst().keySet())
				.map(name -> name.toLowerCase(Locale.ROOT))
				.noneMatch(name -> name.startsWith("x-amz-checksum") || name.equals("x-amz-trailer")
						|| name.equals("x-amz-sdk-checksum-algorithm"));
	}

	@Test
	void presignedLinksAreSignedForThePublicAddressPathStyle() {
		S3Properties properties = store("http://minio:9000", "http://localhost:9000");

		URL get = presignGet(properties, "documents/a.pdf");
		URL put = presignPut(properties, "transcripts/m.json");

		assertThat(get.toString()).startsWith("http://localhost:9000/menton-odtu/documents/a.pdf?");
		assertThat(put.toString()).startsWith("http://localhost:9000/menton-odtu/transcripts/m.json?");
	}

	@Test
	void presignedLinksUseTheStoreAddressWhenNoPublicOneIsSet() {
		URL url = presignGet(store("http://minio:9000", ""), "documents/a.pdf");

		assertThat(url.toString()).startsWith("http://minio:9000/menton-odtu/documents/a.pdf?");
	}

	@Test
	void anEndpointWithoutItsOwnKeysStopsStartup() {
		// The deployment's AWS keys are there, but they are not the store's.
		S3Properties keyless = new S3Properties("us-east-1", "menton-odtu", "http://minio:9000", "", "", "",
				"eu-central-1", "iam-key", "iam-secret");

		assertThatThrownBy(() -> new S3Config().s3Client(keyless, "ODTU"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AWS_S3_ACCESS_KEY_ID and AWS_S3_SECRET_ACCESS_KEY must be set");
	}

	@Test
	void odtuNeverStartsWithItsFilesOnAwsS3() {
		assertThatThrownBy(() -> new S3Config().s3Client(AWS, "ODTU"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("APP_EDITION=ODTU never stores files in AWS S3");
		assertThatThrownBy(() -> S3Config.requireOwnStoreFor(Edition.ODTU,
				store("https://s3.eu-central-1.amazonaws.com", "")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void odtuStartsWithItsOwnStoreOrWithNoStorageAtAll() {
		S3Properties noBucket = new S3Properties("", "", "", "", "", "", "eu-central-1", "", "");

		assertThatCode(() -> S3Config.requireOwnStoreFor(Edition.ODTU, store("http://minio:9000", "")))
				.doesNotThrowAnyException();
		assertThatCode(() -> S3Config.requireOwnStoreFor(Edition.ODTU, noBucket)).doesNotThrowAnyException();
		assertThatCode(() -> S3Config.requireOwnStoreFor(Edition.TEKNOKENT, AWS)).doesNotThrowAnyException();
	}
}
