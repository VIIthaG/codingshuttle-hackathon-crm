package com.flowcrm.search;

import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.search.dto.SearchResponse;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(
            summary = "Global CRM search",
            description =
                    "Role-scoped search across leads, accounts, contacts, deals, tasks, meetings, and calls. "
                            + "Minimum query length is 2. Does not search users. Default limit 24, max 50.")
    public SearchResponse search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        return searchService.search(query, types, limit, principal);
    }
}
