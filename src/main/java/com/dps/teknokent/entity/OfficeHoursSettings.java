package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An academic's meeting defaults, as a mentor's are on their profile. */
@Entity
@Table(name = "office_hours_settings")
@Getter
@Setter
@NoArgsConstructor
public class OfficeHoursSettings {

	@Id
	@Column(name = "instructor_id")
	private UUID instructorId;

	/** How many students they meet at a time; empty means no limit, as for a mentor. */
	@Column(name = "student_capacity")
	private Integer studentCapacity;

	@Column(name = "default_meeting_duration_minutes", nullable = false)
	private int defaultMeetingDurationMinutes = 45;

	@Column(name = "meeting_buffer_minutes", nullable = false)
	private int meetingBufferMinutes = 15;

	/** Whether Menton students see them in the list; off until the academician turns it on. */
	@Column(nullable = false)
	private boolean listed;

	@Column(name = "default_place", length = 200)
	private String defaultPlace;

	@Column(name = "online_link", length = 500)
	private String onlineLink;

	/** Shown to students only when filled in. */
	@Column(name = "contact_email", length = 320)
	private String contactEmail;

	@Column(length = 1000)
	private String about;

	// Batched: the students' list reads every listed academician's lists at once.
	@ElementCollection
	@CollectionTable(name = "office_hours_topic", joinColumns = @JoinColumn(name = "instructor_id"))
	@OrderColumn(name = "display_order")
	@BatchSize(size = 50)
	private List<OfficeHoursTopic> topics = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "office_hours_profile_course", joinColumns = @JoinColumn(name = "instructor_id"))
	@OrderColumn(name = "display_order")
	@BatchSize(size = 50)
	private List<OfficeHoursProfileCourse> courses = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "office_hours_expertise", joinColumns = @JoinColumn(name = "instructor_id"))
	@OrderColumn(name = "display_order")
	@Column(name = "tag", nullable = false, length = 40)
	@BatchSize(size = 50)
	private List<String> expertise = new ArrayList<>();

	/** How many days they usually take to answer a request; empty when they did not say. */
	@Column(name = "response_days")
	private Integer responseDays;

	/** Their own e-mail when a student sends a request; the in-app notification goes either way. */
	@Column(name = "new_request_emails", nullable = false)
	private boolean newRequestEmails = true;

	/** Their own e-mail when a student cancels a meeting. */
	@Column(name = "cancellation_emails", nullable = false)
	private boolean cancellationEmails = true;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
