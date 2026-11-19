package com.dps.teknokent.security.institution;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.util.EmailDomains;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

import lombok.extern.slf4j.Slf4j;

/**
 * "ODTÜ ile giriş yap": ODTÜ's CAS. The browser signs in at CAS and comes back with a one-time ticket; the backend
 * asks CAS who the ticket belongs to ({@code /p3/serviceValidate} for CAS 3.0, {@code /serviceValidate} for 2.0) and
 * never trusts anything else the browser sends. A student's record is the approval (MENTEE), an academic's too
 * (ACADEMICIAN); anyone else is only identified, and joins through Menton's own sign-up. Off unless
 * {@code CAS_LOGIN_ENABLED=true}, and then it refuses to start on a missing or non-https address.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.cas-login.enabled", havingValue = "true")
@EnableConfigurationProperties(CasLoginProperties.class)
public class CasLoginProvider implements InstitutionLoginProvider {

	public static final String ID = "odtu-cas";
	static final String TICKET_PARAM = "ticket";
	static final String STATE_PARAM = "state";
	/** A service ticket; the protocol asks a service to take up to 256 characters. */
	private static final Pattern TICKET = Pattern.compile("ST-[A-Za-z0-9._-]{1,253}");
	/** CAS's failure codes are upper-case words; anything else is not written to the log. */
	private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z_]{1,40}");
	private static final Pattern LOOPBACK_SERVICE = Pattern.compile("http://(localhost|127\\.0\\.0\\.1)(:\\d+)?(/.*)?");
	/** Far above any real answer, which is a few hundred bytes. */
	private static final int MAX_ANSWER_LENGTH = 64 * 1024;

	private final RestClient http;
	private final String loginUrl;
	private final String validateUrl;
	private final String serviceUrl;
	private final List<String> emailDomains;
	private final @Nullable String idAttribute;
	private final String emailAttribute;
	private final @Nullable String nameAttribute;
	private final @Nullable String kindAttribute;
	private final @Nullable String departmentAttribute;
	private final @Nullable String facultyAttribute;
	private final Map<IdentityKind, List<String>> kindValues;

	@Autowired
	public CasLoginProvider(CasLoginProperties properties) {
		this(InstitutionHttp.client(), properties);
	}

	CasLoginProvider(RestClient http, CasLoginProperties properties) {
		this.http = http;
		String baseUrl = requireHttps("CAS_BASE_URL", properties.baseUrl()).replaceAll("/+$", "");
		this.loginUrl = baseUrl + "/login";
		this.validateUrl = baseUrl + validatePath(properties.version());
		this.serviceUrl = requireServiceUrl(properties.serviceUrl());
		this.emailDomains = values(properties.emailDomains()).stream()
				.map(domain -> domain.startsWith("@") ? domain.substring(1) : domain)
				.toList();
		if (emailDomains.isEmpty()) {
			throw new IllegalStateException("CAS_LOGIN_ENABLED=true needs CAS_EMAIL_DOMAINS.");
		}
		this.idAttribute = attributeName(properties.idAttribute());
		this.emailAttribute = require("CAS_ATTR_EMAIL", properties.emailAttribute()).toLowerCase(Locale.ROOT);
		this.nameAttribute = attributeName(properties.nameAttribute());
		this.kindAttribute = attributeName(properties.kindAttribute());
		this.departmentAttribute = attributeName(properties.departmentAttribute());
		this.facultyAttribute = attributeName(properties.facultyAttribute());
		this.kindValues = Map.of(
				IdentityKind.STUDENT, values(properties.studentValues()),
				IdentityKind.ACADEMIC, values(properties.academicValues()),
				IdentityKind.STAFF, values(properties.staffValues()));
		log.info("ODTÜ CAS login is ON ({}).", validateUrl);
	}

	@Override
	public String id() {
		return ID;
	}

	/** Only a student or an academic record stands in for approval; anyone else follows the ordinary sign-up. */
	@Override
	public boolean grantsAccess(InstitutionIdentity identity) {
		return identity.kinds().contains(IdentityKind.STUDENT) || identity.kinds().contains(IdentityKind.ACADEMIC);
	}

	/** CAS names a person and little else, so what an import or the student's own sign-up filed is kept. */
	@Override
	public boolean sendsWholeRecord() {
		return false;
	}

	@Override
	public boolean newStudentsJoinTheProgram() {
		return true;
	}

	@Override
	public String startLogin(String state) {
		// As a variable, so the whole service URL is strictly encoded into the one query value.
		return UriComponentsBuilder.fromUriString(loginUrl)
				.queryParam("service", "{service}")
				.encode()
				.buildAndExpand(serviceFor(state))
				.toUriString();
	}

	@Override
	public InstitutionIdentity completeLogin(String state, Map<String, String> callback) {
		String ticket = callback.get(TICKET_PARAM);
		if (ticket == null || !TICKET.matcher(ticket).matches()) {
			throw new InvalidRequestException(Texts.forRequest("error.casLoginRejected"));
		}
		return switch (validate(state, ticket)) {
		case CasServiceResponse.Failure failure -> throw rejected(failure);
		case CasServiceResponse.Success success -> identity(success);
		};
	}

	/**
	 * The service URL carries the state, and CAS only vouches for a ticket under the exact service it was issued for.
	 * So a ticket from someone else's login fails here: it was issued under their state, not the one this browser
	 * holds. The URL is rebuilt from the configuration and the spent state, never from anything the browser sent.
	 */
	private String serviceFor(String state) {
		return UriComponentsBuilder.fromUriString(serviceUrl)
				.queryParam(STATE_PARAM, "{state}")
				.encode()
				.buildAndExpand(state)
				.toUriString();
	}

	private CasServiceResponse validate(String state, String ticket) {
		URI uri = UriComponentsBuilder.fromUriString(validateUrl)
				.queryParam("service", "{service}")
				.queryParam(TICKET_PARAM, "{ticket}")
				.encode()
				.buildAndExpand(serviceFor(state), ticket)
				.toUri();
		String answer;
		try {
			answer = http.get()
					.uri(uri)
					.accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML)
					.retrieve()
					.body(String.class);
		} catch (RestClientException exception) {
			throw unavailable("validation failed: " + exception.getClass().getSimpleName(), exception);
		}
		if (answer == null || answer.isBlank() || answer.length() > MAX_ANSWER_LENGTH) {
			throw unavailable("validation answered with an empty or oversized body", null);
		}
		try {
			return CasServiceResponse.parse(answer);
		} catch (IllegalArgumentException exception) {
			throw unavailable(exception.getMessage(), exception);
		}
	}

	private InstitutionIdentity identity(CasServiceResponse.Success success) {
		Map<String, List<String>> attributes = success.attributes();
		String id = idAttribute == null ? success.user() : first(attributes, idAttribute).orElse(null);
		if (id == null) {
			throw incomplete(idAttribute, attributes);
		}
		List<String> addresses = attributes.getOrDefault(emailAttribute, List.of()).stream()
				.map(EmailDomains::normalizeEmail)
				.filter(EmailDomains::isEmail)
				.toList();
		if (addresses.isEmpty()) {
			throw incomplete(emailAttribute, attributes);
		}
		// An address outside ODTÜ's domains is one the person typed somewhere, not one ODTÜ gave them; treating it
		// as verified would let them take over the Menton account of whoever really owns it.
		String email = addresses.stream()
				.filter(address -> emailDomains.stream().anyMatch(domain -> EmailDomains.matches(address, domain)))
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
						Texts.forRequest("error.casEmailDomain", "@" + emailDomains.getFirst())));
		// Lower-cased: CAS may hand the user name back the way it was typed, and "E123456" is the same person.
		return new InstitutionIdentity(id.toLowerCase(Locale.ROOT), email, true,
				TextUtils.normalizeOptional(first(attributes, nameAttribute).orElse(null)), kinds(attributes),
				first(attributes, facultyAttribute).orElse(null), null,
				first(attributes, departmentAttribute).orElse(null), null, null, null, false);
	}

	private Set<IdentityKind> kinds(Map<String, List<String>> attributes) {
		Set<IdentityKind> kinds = EnumSet.noneOf(IdentityKind.class);
		if (kindAttribute == null) {
			return kinds;
		}
		for (String sent : attributes.getOrDefault(kindAttribute, List.of())) {
			String value = sent.toLowerCase(Locale.ROOT);
			kindValues.forEach((kind, known) -> {
				if (known.contains(value)) {
					kinds.add(kind);
				}
			});
		}
		return kinds;
	}

	private static Optional<String> first(Map<String, List<String>> attributes, @Nullable String name) {
		return name == null ? Optional.empty() : attributes.getOrDefault(name, List.of()).stream().findFirst();
	}

	/** INVALID_TICKET (spent, expired, made up) or INVALID_SERVICE (issued for another login): the same polite no. */
	private static InvalidRequestException rejected(CasServiceResponse.Failure failure) {
		log.warn("CAS login: ticket refused ({})",
				FAILURE_CODE.matcher(failure.code()).matches() ? failure.code() : "unreadable code");
		return new InvalidRequestException(Texts.forRequest("error.casLoginRejected"));
	}

	/** Names what CAS did send (names only, never a value), so the attribute settings can be put right. */
	private static ResponseStatusException incomplete(@Nullable String missing, Map<String, List<String>> attributes) {
		return unavailable("no usable '" + missing + "' attribute; CAS sent " + attributes.keySet(), null);
	}

	/** Logs what failed, never the ticket or what CAS answered. */
	private static ResponseStatusException unavailable(@Nullable String reason, @Nullable Exception cause) {
		log.warn("CAS login: {}", reason);
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY, Texts.forRequest("error.casLoginFailed"), cause);
	}

	private static String validatePath(@Nullable String version) {
		return switch (require("CAS_VERSION", version)) {
		case "3", "3.0" -> "/p3/serviceValidate";
		case "2", "2.0" -> "/serviceValidate";
		default -> throw new IllegalStateException("CAS_VERSION must be 2.0 or 3.0.");
		};
	}

	/**
	 * Where CAS sends the browser back. It gets the state appended as its only query parameter, so it must have none
	 * of its own; plain http is taken for this machine alone, for trying the login locally.
	 */
	private static String requireServiceUrl(@Nullable String value) {
		String url = require("CAS_SERVICE_URL", value);
		if (!url.startsWith("https://") && !LOOPBACK_SERVICE.matcher(url).matches()) {
			throw new IllegalStateException("CAS_SERVICE_URL must be an https URL.");
		}
		try {
			URI uri = new URI(url);
			if (uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
				throw new IllegalStateException("CAS_SERVICE_URL must be a plain URL: no query and no fragment.");
			}
		} catch (URISyntaxException exception) {
			throw new IllegalStateException("CAS_SERVICE_URL is not a URL.", exception);
		}
		return url;
	}

	private static String requireHttps(String name, @Nullable String value) {
		String url = require(name, value);
		if (!url.startsWith("https://")) {
			throw new IllegalStateException(name + " must be an https URL.");
		}
		return url;
	}

	private static String require(String name, @Nullable String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("CAS_LOGIN_ENABLED=true needs " + name + ".");
		}
		return value.strip();
	}

	private static @Nullable String attributeName(@Nullable String value) {
		return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
	}

	private static List<String> values(@Nullable String commaSeparated) {
		return commaSeparated == null
				? List.of()
				: Arrays.stream(commaSeparated.split(",", -1))
						.map(value -> value.strip().toLowerCase(Locale.ROOT))
						.filter(value -> !value.isEmpty())
						.toList();
	}
}
