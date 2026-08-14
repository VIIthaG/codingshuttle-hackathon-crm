package com.flowcrm.workqueue;

import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.workqueue.dto.WorkqueueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workqueue")
@Tag(name = "Workqueue")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class WorkqueueController {

    private final WorkqueueService workqueueService;

    public WorkqueueController(WorkqueueService workqueueService) {
        this.workqueueService = workqueueService;
    }

    @GetMapping
    @Operation(
            summary = "Next actions",
            description =
                    "Overdue/today/upcoming tasks plus today's and upcoming scheduled meetings and planned calls. "
                            + "SALES_REP sees assigned work only. ADMIN sees the team unless assignedToId is set.")
    public WorkqueueResponse get(
            @RequestParam(required = false) UUID assignedToId, @AuthenticationPrincipal UserPrincipal principal) {
        return workqueueService.get(assignedToId, principal);
    }
}
