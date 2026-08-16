package com.flowcrm.assistant;

import com.flowcrm.common.exception.ServiceUnavailableException;

public final class AiUnavailable {

    public static final String USER_MESSAGE =
            "Flow AI is temporarily unavailable. Your CRM data is unaffected.";

    private AiUnavailable() {}

    public static ServiceUnavailableException exception() {
        return new ServiceUnavailableException(USER_MESSAGE);
    }
}
