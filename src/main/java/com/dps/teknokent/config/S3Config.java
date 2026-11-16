package com.dps.teknokent.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dps.teknokent.domain.Edition;

import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

	@Bean
	S3Client s3Client(S3Properties properties, @Value("${app.edition:TEKNOKENT}") String edition) {
		requireOwnStoreFor(Edition.parse(edition), properties);
		// The SDK default has no cap on a whole call (retries included). 30 s, not less: the backend itself uploads
		// documents of up to 50 MB.
		var builder = S3Client.builder()
				.region(Region.of(properties.effectiveRegion()))
				.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30)));
		properties.credentials().ifPresent(builder::credentialsProvider);
		properties.endpointUri().ifPresent(endpoint -> {
			requireKeysFor(properties);
			// S3-compatible stores serve buckets as a path, not as a subdomain, and not all of them accept the
			// checksums the SDK adds to every upload by default; send those only where an operation needs them.
			builder.endpointOverride(endpoint)
					.forcePathStyle(true)
					.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
					.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
		});
		return builder.build();
	}

	@Bean
	S3Presigner s3Presigner(S3Properties properties) {
		var builder = S3Presigner.builder().region(Region.of(properties.effectiveRegion()));
		properties.credentials().ifPresent(builder::credentialsProvider);
		// A presigned link is signed for its host, so it is built for the address the browser will use.
		properties.presignEndpointUri().ifPresent(endpoint -> builder.endpointOverride(endpoint)
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()));
		return builder.build();
	}

	/**
	 * ODTÜ's files stay on ODTÜ's servers (KVKK): with a bucket set, the ODTU edition starts only when that bucket
	 * is in its own store. No bucket at all is allowed; storage then simply stays unconfigured.
	 */
	static void requireOwnStoreFor(Edition edition, S3Properties properties) {
		if (edition == Edition.ODTU && properties.hasBucket() && properties.pointsAtAws()) {
			throw new IllegalStateException("APP_EDITION=ODTU never stores files in AWS S3: set AWS_S3_ENDPOINT to "
					+ "its own object store (MinIO), or leave AWS_S3_BUCKET empty.");
		}
	}

	/**
	 * Without its own keys an S3-compatible store would be sent whatever the default AWS credential chain finds on
	 * the machine, which is never right for it.
	 */
	private static void requireKeysFor(S3Properties properties) {
		if (properties.credentials().isEmpty()) {
			throw new IllegalStateException(
					"AWS_S3_ENDPOINT is set, so AWS_S3_ACCESS_KEY_ID and AWS_S3_SECRET_ACCESS_KEY must be set too.");
		}
	}
}
