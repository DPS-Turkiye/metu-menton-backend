package com.dps.teknokent.domain;

/** Something on the placement screen the coordinator should look at; none of these blocks a change. */
public enum IskurWarning {
	/** The team does not have the planned nine people. */
	TEAM_SIZE,
	/** A leader leads more than four members. */
	SUBGROUP_SIZE,
	/** The team has no leader yet. */
	NO_LEADER,
	/** A leader works fewer than two days a week, the minimum the leadership application asks for. */
	LEADER_FEW_DAYS,
	/** A member is in no leader's sub-group. */
	NO_LEADER_ASSIGNED,
	/** Nobody has said how many days a week the intern works. */
	DAYS_MISSING
}
