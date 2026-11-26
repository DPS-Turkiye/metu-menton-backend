package com.dps.teknokent.domain;

/**
 * A leader's request for help from another team (student handbook p.12, p.14, p.18: "Başka ekipten destek lazım →
 * Liderin talep açar"). That team's leader accepts or declines it.
 */
public enum IskurTeamSupportStatus {
	OPEN,
	ACCEPTED,
	DECLINED
}
