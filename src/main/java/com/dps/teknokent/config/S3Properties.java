package com.dps.teknokent.config;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Where stored files go. With no endpoint the clients talk to AWS S3, as they always have. An endpoint points them
 * at an S3-compatible store on our own servers instead (MinIO for the ODTÜ edition). Presigned links are signed for
 * {@code publicEndpoint} when it is set, because a browser cannot reach the store's internal host name.
 *
 * <p>The {@code aws*} values are the deployment's AWS region and keys (SES uses them too). They stand in for the
 * store's own region, and for its keys only when there is no endpoint, so a non-AWS store is never sent AWS keys.
 * Blank counts as unset here, because an env var declared empty never reaches a placeholder's default.
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
		String region,
		String bucket,
		String endpoint,
		String publicEndpoint,
		String accessKeyId,
		String secretAccessKey,
		String awsRegion,
		String awsAccessKeyId,
		String awsSecretAccessKey) {

	private static final List<String> AWS_DOMAINS = List.of("amazonaws.com", "amazonaws.com.cn", "api.aws");

	public S3Properties(
			@Nullable String region,
			@Nullable String bucket,
			@Nullable String endpoint,
			@Nullable String publicEndpoint,
			@Nullable String accessKeyId,
			@Nullable String secretAccessKey,
			@Nullable String awsRegion,
			@Nullable String awsAccessKeyId,
			@Nullable String awsSecretAccessKey) {
		this.region = trimmed(region);
		this.bucket = trimmed(bucket);
		this.endpoint = trimmed(endpoint);
		this.publicEndpoint = trimmed(publicEndpoint);
		this.accessKeyId = trimmed(accessKeyId);
		this.secretAccessKey = trimmed(secretAccessKey);
		this.awsRegion = trimmed(awsRegion);
		this.awsAccessKeyId = trimmed(awsAccessKeyId);
		this.awsSecretAccessKey = trimmed(awsSecretAccessKey);
	}

	public boolean hasBucket() {
		return !bucket.isEmpty();
	}

	/** The store's region, else the deployment's AWS region. */
	public String effectiveRegion() {
		return region.isEmpty() ? awsRegion : region;
	}

	/** The store's own address; empty means AWS S3. */
	public Optional<URI> endpointUri() {
		return uri(endpoint, "AWS_S3_ENDPOINT");
	}

	/** The address presigned links are signed for: the public one when set, otherwise the store's own. */
	public Optional<URI> presignEndpointUri() {
		if (publicEndpoint.isEmpty()) {
			return endpointUri();
		}
		if (endpoint.isEmpty()) {
			// Alone it would quietly turn AWS presigned links into path-style links on another host.
			throw new IllegalStateException("AWS_S3_PUBLIC_ENDPOINT is set without AWS_S3_ENDPOINT.");
		}
		return uri(publicEndpoint, "AWS_S3_PUBLIC_ENDPOINT");
	}

	/**
	 * Static keys: the store's own pair, or, for AWS S3 only, the deployment's AWS pair. Empty means the SDK's
	 * default credential chain, which applies to AWS S3 alone.
	 */
	public Optional<AwsCredentialsProvider> credentials() {
		if (!accessKeyId.isEmpty() && !secretAccessKey.isEmpty()) {
			return Optional.of(keys(accessKeyId, secretAccessKey));
		}
		if (endpoint.isEmpty() && !awsAccessKeyId.isEmpty() && !awsSecretAccessKey.isEmpty()) {
			return Optional.of(keys(awsAccessKeyId, awsSecretAccessKey));
		}
		return Optional.empty();
	}

	/**
	 * Whether files would land at Amazon: no endpoint at all, or one on an AWS domain. A name of our own that
	 * points at S3 (a CNAME) cannot be told apart here; the endpoint is the operators' setting, not user input.
	 */
	public boolean pointsAtAws() {
		return endpointUri()
				.map(uri -> {
					String host = uri.getHost().toLowerCase(Locale.ROOT);
					String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
					return AWS_DOMAINS.stream().anyMatch(aws -> name.equals(aws) || name.endsWith("." + aws));
				})
				.orElse(true);
	}

	// The messages name the variable but never echo its value, which may carry credentials (user:pass@host).
	@SuppressWarnings("UnusedException") // the cause's message would repeat the value
	private static Optional<URI> uri(String value, String variable) {
		if (value.isEmpty()) {
			return Optional.empty();
		}
		URI uri;
		try {
			uri = URI.create(value);
		} catch (IllegalArgumentException invalid) {
			throw new IllegalStateException(variable + " is not a valid URL.");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
			throw new IllegalStateException(
					variable + " must be an http(s) URL with a host, such as http://minio:9000.");
		}
		if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
			throw new IllegalStateException(variable + " must not carry credentials, a query or a fragment.");
		}
		return Optional.of(uri);
	}

	private static AwsCredentialsProvider keys(String accessKeyId, String secretAccessKey) {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
	}

	private static String trimmed(@Nullable String value) {
		return value == null ? "" : value.strip();
	}
}
