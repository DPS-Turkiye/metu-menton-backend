package com.dps.teknokent.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.dps.teknokent.config.RateLimitingConfig;
import com.dps.teknokent.config.RateLimitingConfig.TokenBucket;
import com.dps.teknokent.security.AppUserPrincipal;
import com.dps.teknokent.security.FirebaseIdentity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

	private final RateLimitingConfig rateLimitingConfig;

	private static final String[] RATE_LIMITED_PATHS = {
			"/api/assistant/",
	};

	/** Exposed to browsers in SecurityConfig, so the CV upload can say how long to wait. */
	public static final String RETRY_AFTER_HEADER = "X-Rate-Limit-Retry-After-Seconds";

	private static final String CV_PARSE_PATH = "/api/profile-analyses";
	private static final String CV_PREVIEW_PATH = CV_PARSE_PATH + "/preview";
	private static final String SYLLABUS_PARSE_PATH = "/api/coordinator/programs/syllabus/phases";
	private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();
	private static final Pattern WEEKLY_PROGRAM_READ_PATH = Pattern
			.compile("/api/coordinator/programs/[^/]+/weekly-program/read");

	/**
	 * Each CV, syllabus or weekly program parse runs OCR and a paid LLM call, so it gets a far tighter budget than the
	 * general limit.
	 */
	private static final long CV_PARSE_CAPACITY = 10;
	private static final long CV_PARSE_WINDOW_MS = 10 * 60_000;

	/**
	 * The preview opens to any verified Firebase login, account or not, and sign-up is open, so the per-account
	 * budget alone would let a script that makes logins parse without end. Generous enough for a room of people
	 * signing up behind one office or campus address.
	 */
	private static final long CV_PREVIEW_PER_ADDRESS_CAPACITY = 30;

	/**
	 * ODTÜ's student sign-up creates an account, and Firebase hands out logins freely, so each login gets a few tries.
	 * Keyed per login rather than per address: every request comes through the web proxy, so the address would put a
	 * whole campus signing up on one budget.
	 */
	private static final String STUDENT_SIGNUP_PATH = "/api/auth/register/student";
	private static final long STUDENT_SIGNUP_CAPACITY = 10;
	private static final long STUDENT_SIGNUP_WINDOW_MS = 10 * 60_000;

	/**
	 * Starting an institution login (ODTÜ's CAS, OTG, the dev stub) leaves a state in Redis for ten minutes, and the
	 * web proxy sends every start under its one service login, so there is no caller to tell apart: one budget for all
	 * of them bounds that state. The web keeps a per-address budget in front of it; this is wide enough for a whole
	 * campus signing in on a Monday morning.
	 */
	private static final Pattern INSTITUTION_LOGIN_START_PATH = Pattern.compile("/api/auth/institution/[^/]+/start");
	private static final long INSTITUTION_LOGIN_STARTS_PER_MINUTE = 1_200;

	/**
	 * A calendar feed is fetched by the subscriber's calendar server, not by a person, and the frontend's server
	 * passes every fetch on from one address, so the budget is per link. A course link may have a subscription per
	 * student, each refreshed every hour or so: 120 in ten minutes covers a large class and stops a loop. Guessing
	 * links costs nothing here, since each guess is a new link; 256 random bits make guessing hopeless instead.
	 */
	private static final String CALENDAR_FEED_PATH = "/api/office-hours/calendar-feeds/";
	private static final long CALENDAR_FEED_CAPACITY = 120;
	private static final long CALENDAR_FEED_WINDOW_MS = 10 * 60_000;
	/**
	 * Every link together: each made-up link is a new per-link budget, so a flood of them is stopped here instead.
	 * The frontend's server sends all of them under one login, so there is no caller to tell apart.
	 */
	private static final long CALENDAR_FEEDS_PER_MINUTE = 1_000;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		List<TokenBucket> buckets = bucketsFor(request);
		if (buckets.isEmpty()) {
			filterChain.doFilter(request, response);
			return;
		}

		long remaining = Long.MAX_VALUE;
		// In order, stopping at the first refusal: a caller over its own budget spends nothing from the address's.
		for (TokenBucket bucket : buckets) {
			if (!bucket.tryConsume(1)) {
				long secondsToWait = bucket.getSecondsToWait();
				response.addHeader(RETRY_AFTER_HEADER, String.valueOf(secondsToWait));
				response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),
						"Too many requests. Please retry after " + secondsToWait + " seconds.");
				return;
			}
			remaining = Math.min(remaining, bucket.getRemainingTokens());
		}
		response.addHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));
		filterChain.doFilter(request, response);
	}

	private List<TokenBucket> bucketsFor(HttpServletRequest request) {
		// Decoded, as the router sees it: "/weekly-program/%72ead" must not slip past the tight budget.
		String path = URL_PATH_HELPER.getPathWithinApplication(request);
		boolean post = "POST".equalsIgnoreCase(request.getMethod());
		if (post && isCvPreview(path)) {
			return List.of(
					rateLimitingConfig.resolveBucket(
							"cv_parse:" + getCallerIdentifier(request), CV_PARSE_CAPACITY, CV_PARSE_WINDOW_MS),
					rateLimitingConfig.resolveBucket(
							"cv_preview_ip:" + clientAddress(request), CV_PREVIEW_PER_ADDRESS_CAPACITY,
							CV_PARSE_WINDOW_MS));
		}
		if (post && isCvParse(path)) {
			return List.of(rateLimitingConfig.resolveBucket(
					"cv_parse:" + getCallerIdentifier(request), CV_PARSE_CAPACITY, CV_PARSE_WINDOW_MS));
		}
		if (post && STUDENT_SIGNUP_PATH.equals(path)) {
			return List.of(rateLimitingConfig.resolveBucket(
					"student_signup:" + getCallerIdentifier(request), STUDENT_SIGNUP_CAPACITY,
					STUDENT_SIGNUP_WINDOW_MS));
		}
		if (post && INSTITUTION_LOGIN_START_PATH.matcher(path).matches()) {
			return List.of(rateLimitingConfig.resolveBucket("institution_login_start_all",
					INSTITUTION_LOGIN_STARTS_PER_MINUTE, 60_000));
		}
		if ("GET".equalsIgnoreCase(request.getMethod()) && path.startsWith(CALENDAR_FEED_PATH)) {
			return List.of(
					rateLimitingConfig.resolveBucket("calendar_feed:" + linkKey(path), CALENDAR_FEED_CAPACITY,
							CALENDAR_FEED_WINDOW_MS),
					rateLimitingConfig.resolveBucket("calendar_feed_all", CALENDAR_FEEDS_PER_MINUTE, 60_000));
		}
		if (shouldApplyRateLimit(request.getRequestURI())) {
			return List.of(rateLimitingConfig.resolveBucket(clientAddress(request)));
		}
		return List.of();
	}

	/** The link's hash, so the token itself never becomes a key in Redis or in memory. */
	static String linkKey(String path) {
		String token = path.substring(CALENDAR_FEED_PATH.length());
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException impossible) {
			// Every Java runtime ships SHA-256.
			throw new IllegalStateException(impossible);
		}
	}

	private static boolean isCvPreview(String path) {
		return CV_PREVIEW_PATH.equals(path) || path.startsWith(CV_PREVIEW_PATH + "/documents/");
	}

	private static boolean isCvParse(String path) {
		return CV_PARSE_PATH.equals(path)
				|| SYLLABUS_PARSE_PATH.equals(path)
				|| WEEKLY_PROGRAM_READ_PATH.matcher(path).matches();
	}

	/**
	 * Keyed per account rather than per address, so founders sharing one office network do not use up each
	 * other's budget. This filter runs after Spring Security, so the caller is already resolved; the service
	 * Basic login is one shared identity and falls back to the address.
	 */
	private String getCallerIdentifier(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication == null ? null : authentication.getPrincipal();
		if (principal instanceof AppUserPrincipal user) {
			return "user:" + user.id();
		}
		if (principal instanceof FirebaseIdentity identity) {
			return "firebase:" + identity.uid();
		}
		return "ip:" + clientAddress(request);
	}

	private boolean shouldApplyRateLimit(String path) {
		for (String pattern : RATE_LIMITED_PATHS) {
			if (path.startsWith(pattern)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * CloudFront appends the address it saw to whatever X-Forwarded-For the caller sent, so only the last
	 * entry is ours; the first, and X-Real-IP, are whatever the caller chose to write.
	 */
	// ponytail: trusts exactly one proxy hop (CloudFront). If 8080 is ever reachable directly, or a load
	// balancer is added in front, this must key on getRemoteAddr() or skip that many trailing entries.
	static String clientAddress(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			String[] hops = forwardedFor.split(",");
			return hops[hops.length - 1].trim();
		}
		return request.getRemoteAddr();
	}
}
