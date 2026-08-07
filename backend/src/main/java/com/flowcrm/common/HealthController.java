package com.flowcrm.common;

import com.flowcrm.common.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health")
public class HealthController {

    @GetMapping("/health")
    @SecurityRequirements
    @Operation(summary = "Health check", description = "Unauthenticated liveness probe.")
    @ApiResponse(responseCode = "200", description = "Service is up")
    public HealthResponse health() {
        return new HealthResponse("UP", "flowcrm");
    }
}
