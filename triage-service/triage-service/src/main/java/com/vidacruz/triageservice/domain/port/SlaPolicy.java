package com.vidacruz.triageservice.domain.port;

import com.vidacruz.triageservice.domain.model.TriageLevel;

/** RN-02 SLA lookup boundary. */
public interface SlaPolicy {

	int minutesFor(TriageLevel level);
}
