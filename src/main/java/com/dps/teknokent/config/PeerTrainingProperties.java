package com.dps.teknokent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How someone becomes an Akran Mentoru. {@code inAppQuiz}: passing Menton's own quiz awards the badge (off on ODTÜ,
 * whose training and test run on Prism and whose coordinators approve each mentor). {@code internsOnly}: only the
 * program's İŞKUR interns may get the badge (off since 24 Nov: every active student may apply, interns or not).
 */
@ConfigurationProperties(prefix = "app.peer-training")
public record PeerTrainingProperties(@DefaultValue("false") boolean inAppQuiz,
		@DefaultValue("false") boolean internsOnly) {
}
