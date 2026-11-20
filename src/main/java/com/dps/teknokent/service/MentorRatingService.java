package com.dps.teknokent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.dto.responses.MentorRatingResponse;
import com.dps.teknokent.repository.MeetingFeedbackRepository;
import com.dps.teknokent.repository.MeetingFeedbackRepository.MentorshipRating;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MentorRatingService {

	private final MeetingFeedbackRepository feedbackRepository;

	/** The overall average weighs each mentorship by its feedback count, so it equals the average of all feedback. */
	@Transactional(readOnly = true)
	public MentorRatingResponse rating(UUID mentorId) {
		List<MentorshipRating> rows = feedbackRepository.ratingsByMentorship(mentorId);
		long feedback = rows.stream().mapToLong(MentorshipRating::getFeedbackCount).sum();
		Double average = feedback == 0
				? null
				: rows.stream().mapToDouble(row -> row.getAverage() * row.getFeedbackCount()).sum() / feedback;
		List<MentorRatingResponse.Mentorship> mentorships = new ArrayList<>();
		for (MentorshipRating row : rows) {
			UUID requestId = row.getRequestId();
			if (requestId != null) {
				mentorships
						.add(new MentorRatingResponse.Mentorship(requestId, row.getAverage(), row.getFeedbackCount()));
			}
		}
		return new MentorRatingResponse(average, feedback, mentorships);
	}
}
