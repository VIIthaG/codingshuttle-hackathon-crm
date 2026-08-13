package com.flowcrm.deal;

import com.flowcrm.enums.DealStage;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed deal pipeline transitions. CLOSED_WON and CLOSED_LOST are terminal.
 */
public final class DealStageTransitions {

    private static final Map<DealStage, Set<DealStage>> ALLOWED = new EnumMap<>(DealStage.class);

    static {
        ALLOWED.put(DealStage.PROSPECTING, EnumSet.of(DealStage.QUALIFICATION, DealStage.CLOSED_LOST));
        ALLOWED.put(DealStage.QUALIFICATION, EnumSet.of(DealStage.PROPOSAL, DealStage.CLOSED_LOST));
        ALLOWED.put(DealStage.PROPOSAL, EnumSet.of(DealStage.NEGOTIATION, DealStage.CLOSED_LOST));
        ALLOWED.put(DealStage.NEGOTIATION, EnumSet.of(DealStage.CLOSED_WON, DealStage.CLOSED_LOST));
        ALLOWED.put(DealStage.CLOSED_WON, EnumSet.noneOf(DealStage.class));
        ALLOWED.put(DealStage.CLOSED_LOST, EnumSet.noneOf(DealStage.class));
    }

    private DealStageTransitions() {
    }

    public static boolean canTransition(DealStage from, DealStage to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<DealStage> allowedTargets(DealStage from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }

    public static boolean isTerminal(DealStage stage) {
        return stage == DealStage.CLOSED_WON || stage == DealStage.CLOSED_LOST;
    }

    public static boolean isOpen(DealStage stage) {
        return !isTerminal(stage);
    }

    public static int defaultProbability(DealStage stage) {
        return switch (stage) {
            case PROSPECTING -> 10;
            case QUALIFICATION -> 25;
            case PROPOSAL -> 50;
            case NEGOTIATION -> 75;
            case CLOSED_WON -> 100;
            case CLOSED_LOST -> 0;
        };
    }
}
