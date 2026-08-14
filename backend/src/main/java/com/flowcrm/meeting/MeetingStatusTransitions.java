package com.flowcrm.meeting;

import com.flowcrm.enums.MeetingStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class MeetingStatusTransitions {

    private static final Map<MeetingStatus, Set<MeetingStatus>> ALLOWED = new EnumMap<>(MeetingStatus.class);

    static {
        ALLOWED.put(MeetingStatus.SCHEDULED, EnumSet.of(MeetingStatus.COMPLETED, MeetingStatus.CANCELLED));
        ALLOWED.put(MeetingStatus.COMPLETED, EnumSet.noneOf(MeetingStatus.class));
        ALLOWED.put(MeetingStatus.CANCELLED, EnumSet.noneOf(MeetingStatus.class));
    }

    private MeetingStatusTransitions() {}

    public static boolean canTransition(MeetingStatus from, MeetingStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(MeetingStatus status) {
        return status == MeetingStatus.COMPLETED || status == MeetingStatus.CANCELLED;
    }
}
