package com.dps.teknokent.domain;

/**
 * Where a student stands with their peer program's training. BLOCKED: their badge there was taken back, so only a
 * coordinator or admin can award it again (the quiz cannot). VIDEO and QUIZ are the in-app training; the PRISM_
 * states are ODTÜ's, where the training runs on Prism and a coordinator approves each mentor: TODO (not done yet),
 * PENDING (the student said they finished it; waiting for the coordinator) and RETAKE (asked to take the test again).
 * INTERNS_ONLY: with the interns-only setting on, the program's peer mentors come from its İŞKUR interns, and this
 * student is not one.
 */
public enum TrainingState {
	NOT_AVAILABLE,
	VIDEO,
	QUIZ,
	BADGE_ACTIVE,
	BLOCKED,
	PRISM_TODO,
	PRISM_PENDING,
	PRISM_RETAKE,
	INTERNS_ONLY
}
