package com.association.assofin.user;


import com.association.assofin.user.dto.UserProfileResponseDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("users")
@RequiredArgsConstructor
@Tag(name = "users")
public class UserController {

    private final UserService userService;

    /**
     * Endpoint 1: Fetch the currently authenticated user's profile.
     * Requires only that the user is authenticated.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDTO> getConnectedUserProfile(Authentication authentication) {
        // Extract user context directly from Spring Security Authentication authentication object now is handled by the RestAuthenticationEntryPoint
        String username = authentication.getName();
        UserProfileResponseDTO profile = userService.getProfileByUsername(username);
        return ResponseEntity.ok(profile);
    }

    /**
     * Endpoint 2: Fetch any target user profile by ID.
     * Requires specific administrative permission 'user:read'.
     */
   /* todo implement read user for admins allowed to read the users profile
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:read')")
    public ResponseEntity<UserProfileResponseDTO> getUserProfileById(@PathVariable Long userId) {
        UserProfileResponseDTO profile = userService.getUserProfileById(userId);
        return ResponseEntity.ok(profile);
    }*/
}
