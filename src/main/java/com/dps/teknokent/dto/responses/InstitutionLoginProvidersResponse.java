package com.dps.teknokent.dto.responses;

import java.util.List;

/** The ids of the institution logins that are switched on (for example {@code odtu-cas}). */
public record InstitutionLoginProvidersResponse(List<String> providers) {
}
