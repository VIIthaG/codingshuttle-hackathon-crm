package com.flowcrm.call;

import com.flowcrm.enums.CallStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CallStatusTransitions {

    private static final Map<CallStatus, Set<CallStatus>> ALLOWED = new EnumMap<>(CallStatus.class);

    static {
        ALLOWED.put(CallStatus.PLANNED, EnumSet.of(CallStatus.COMPLETED, CallStatus.CANCELLED));
        ALLOWED.put(CallStatus.COMPLETED, EnumSet.noneOf(CallStatus.class));
        ALLOWED.put(CallStatus.CANCELLED, EnumSet.noneOf(CallStatus.class));
    }

    private CallStatusTransitions() {}

    public static boolean canTransition(CallStatus from, CallStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(CallStatus status) {
        return status == CallStatus.COMPLETED || status == CallStatus.CANCELLED;
    }
}
