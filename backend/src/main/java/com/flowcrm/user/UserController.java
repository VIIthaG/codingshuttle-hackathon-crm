package com.flowcrm.user;

import com.flowcrm.auth.dto.UserResponse;
import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.enums.Role;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "List users", description = "ADMIN only. Used for owner assignment on accounts and contacts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User directory"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<UserResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can list users");
        }
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(user -> new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole()))
                .toList();
    }
}
