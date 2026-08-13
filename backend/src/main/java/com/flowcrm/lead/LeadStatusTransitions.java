package com.flowcrm.lead;

import com.flowcrm.enums.LeadStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed ordinary PATCH pipeline transitions.
 * CONVERTED is not reachable here — only POST /leads/{id}/convert may set it.
 * Terminal statuses LOST and CONVERTED cannot move further.
 */
public final class LeadStatusTransitions {

    private static final Map<LeadStatus, Set<LeadStatus>> ALLOWED = new EnumMap<>(LeadStatus.class);

    static {
        ALLOWED.put(LeadStatus.NEW, EnumSet.of(LeadStatus.CONTACTED, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.CONTACTED, EnumSet.of(LeadStatus.QUALIFIED, LeadStatus.LOST));
        ALLOWED.put(LeadStatus.QUALIFIED, EnumSet.of(LeadStatus.LOST));
        ALLOWED.put(LeadStatus.LOST, EnumSet.noneOf(LeadStatus.class));
        ALLOWED.put(LeadStatus.CONVERTED, EnumSet.noneOf(LeadStatus.class));
    }

    private LeadStatusTransitions() {
    }

    public static boolean canTransition(LeadStatus from, LeadStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<LeadStatus> allowedTargets(LeadStatus from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }
}
