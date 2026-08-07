package com.flowcrm.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Task status")
public enum TaskStatus {
    OPEN,
    COMPLETED,
    CANCELLED
}
