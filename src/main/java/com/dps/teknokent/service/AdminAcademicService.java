package com.dps.teknokent.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.dto.responses.AdminAcademicResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.AppUserIdentity;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.AppUserIdentityRepository;
import com.dps.teknokent.repository.InstitutionAdvisingRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;
import com.dps.teknokent.security.AdminAccess;
import com.dps.teknokent.util.MeetingSlotTimes;

/** Admin › Kullanıcılar › Akademisyenler: every academician the institution lists, signed in or not. */
@Service
@Transactional(readOnly = true)
public class AdminAcademicService {

	private final DomainLookupService domainLookupService;
	private final InstitutionPersonRepository people;
	private final InstitutionAdvisingRepository advising;
	private final AppUserIdentityRepository identities;
	private final CoordinatorProgramService coordinatorProgramService;
	private final OfficeHoursUserRepository officeHoursPeople;
	private final OfficeHoursMeetingRepository meetings;
	private final Clock clock;

	@Autowired
	public AdminAcademicService(DomainLookupService domainLookupService, InstitutionPersonRepository people,
			InstitutionAdvisingRepository advising, AppUserIdentityRepository identities,
			CoordinatorProgramService coordinatorProgramService, OfficeHoursUserRepository officeHoursPeople,
			OfficeHoursMeetingRepository meetings) {
		this(domainLookupService, people, advising, identities, coordinatorProgramService, officeHoursPeople,
				meetings, Clock.systemUTC());
	}

	AdminAcademicService(DomainLookupService domainLookupService, InstitutionPersonRepository people,
			InstitutionAdvisingRepository advising, AppUserIdentityRepository identities,
			CoordinatorProgramService coordinatorProgramService, OfficeHoursUserRepository officeHoursPeople,
			OfficeHoursMeetingRepository meetings, Clock clock) {
		this.domainLookupService = domainLookupService;
		this.people = people;
		this.advising = advising;
		this.identities = identities;
		this.coordinatorProgramService = coordinatorProgramService;
		this.officeHoursPeople = officeHoursPeople;
		this.meetings = meetings;
		this.clock = clock;
	}

	public List<AdminAcademicResponse> list(UUID actorId) {
		requireAdmin(actorId);
		return directory(true);
	}

	/**
	 * An Office Hours program's Katılımcılar › Akademisyenler: every academician the institution lists as active
	 * takes part, without invites (board). For the program's coordinators and the admin; no e-mail or sign-in time.
	 */
	public List<AdminAcademicResponse> forProgram(UUID actorId, UUID programId) {
		Program program = coordinatorProgramService.requireManagedProgram(actorId, programId);
		if (!program.has(ProgramFeature.OFFICE_HOURS)) {
			throw new InvalidRequestException("Academicians are listed for Office Hours programs only.");
		}
		return directory(false).stream().filter(AdminAcademicResponse::active).toList();
	}

	private List<AdminAcademicResponse> directory(boolean contactDetails) {
		List<InstitutionPerson> academics = people.findByAcademicTrueOrderByFullNameAsc();
		Map<String, Long> advisees = advising.countAdviseesByAdvisor().stream()
				.collect(Collectors.toMap(load -> key(load.getInstitution(), load.getAdvisorPersistentId()),
						InstitutionAdvisingRepository.AdvisorLoad::getAdvisees, Long::sum));
		List<UUID> userIds = academics.stream()
				.map(InstitutionPerson::getUser)
				.filter(Objects::nonNull)
				.map(AppUser::getId)
				.toList();
		Map<UUID, Instant> lastLogins = !contactDetails || userIds.isEmpty()
				? Map.of()
				: identities.findByUserIdIn(userIds).stream()
						.collect(Collectors.toMap(identity -> identity.getUser().getId(),
								AppUserIdentity::getLastLoginAt, (a, b) -> a.isAfter(b) ? a : b));
		// Office Hours numbers are the admin's columns only; the program's coordinators get none.
		Map<UUID, AdminAcademicResponse.OfficeHours> officeHours = contactDetails ? officeHoursOf(userIds) : Map.of();
		return academics.stream()
				.map(person -> {
					UUID userId = person.getUser() == null ? null : person.getUser().getId();
					return new AdminAcademicResponse(
							person.getId(),
							userId,
							person.getFullName(),
							contactDetails ? person.getEmail() : null,
							person.getAcademicTitle(),
							person.getFaculty(),
							person.getDepartmentCode(),
							person.getDepartmentName(),
							person.isActive(),
							userId != null,
							contactDetails && userId != null ? lastLogins.get(userId) : null,
							advisees.getOrDefault(key(person.getInstitution(), person.getPersistentId()), 0L),
							officeHoursFor(contactDetails, officeHours, userId));
				})
				.toList();
	}

	/**
	 * Each signed-in academician's Office Hours numbers, by account, in two queries whatever the list's length: the
	 * office-hours person with whether it has hours, then this month's live meetings for all of them at once.
	 */
	private Map<UUID, AdminAcademicResponse.OfficeHours> officeHoursOf(List<UUID> accountIds) {
		if (accountIds.isEmpty()) {
			return Map.of();
		}
		Instant now = clock.instant();
		List<OfficeHoursUserRepository.PersonHours> persons = officeHoursPeople.findHoursByAccountIdIn(accountIds,
				now);
		if (persons.isEmpty()) {
			return Map.of();
		}
		ZonedDateTime monthStart = now.atZone(MeetingSlotTimes.APP_ZONE).toLocalDate().withDayOfMonth(1)
				.atStartOfDay(MeetingSlotTimes.APP_ZONE);
		Map<UUID, Long> meetingsByPerson = meetings
				.countLiveByInstructorBetween(
						persons.stream().map(OfficeHoursUserRepository.PersonHours::getPersonId).toList(),
						monthStart.toInstant(), monthStart.plusMonths(1).toInstant())
				.stream()
				.collect(Collectors.toMap(OfficeHoursMeetingRepository.InstructorMeetings::getInstructorId,
						OfficeHoursMeetingRepository.InstructorMeetings::getMeetings));
		return persons.stream().collect(Collectors.toMap(OfficeHoursUserRepository.PersonHours::getAccountId,
				person -> new AdminAcademicResponse.OfficeHours(person.isHoursDefined(),
						meetingsByPerson.getOrDefault(person.getPersonId(), 0L))));
	}

	/** The admin's numbers for one row: none set and no meetings for someone with no office-hours person yet. */
	private static AdminAcademicResponse.@Nullable OfficeHours officeHoursFor(boolean admin,
			Map<UUID, AdminAcademicResponse.OfficeHours> officeHours, @Nullable UUID userId) {
		if (!admin) {
			return null;
		}
		AdminAcademicResponse.OfficeHours numbers = userId == null ? null : officeHours.get(userId);
		if (numbers == null) {
			return new AdminAcademicResponse.OfficeHours(false, 0);
		}
		return numbers;
	}

	private void requireAdmin(UUID actorId) {
		AdminAccess.requireAdmin(domainLookupService.requireUser(actorId));
	}

	private static String key(String institution, String persistentId) {
		return institution + "|" + persistentId;
	}
}
