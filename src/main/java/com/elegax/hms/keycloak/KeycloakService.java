package com.elegax.hms.keycloak;

import com.elegax.hms.patients.entity.StaffMember;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class KeycloakService {

    public static final String TEMPORARY_STAFF_PASSWORD = "1234";

    private final KeycloakFeignClient keycloakFeignClient;

    @Value("${keycloak.realm:hmsRealm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.grant-type:client_credentials}")
    private String grantType;

    public KeycloakService(KeycloakFeignClient keycloakFeignClient) {
        this.keycloakFeignClient = keycloakFeignClient;
    }

    public ProvisionedUser provisionStaffUser(StaffMember staffMember) {
        validateStaffMember(staffMember);

        String username = staffMember.getEmail().trim().toLowerCase(Locale.ROOT);
        String accessToken = bearerToken();
        Map<String, Object> existingUser = findUser(accessToken, username);
        if (existingUser != null) {
            String userId = String.valueOf(existingUser.get("id"));
            keycloakFeignClient.updateUser(accessToken, realm, userId, Map.of("enabled", true));
            assignGroup(accessToken, userId, staffMember.getStaffRole());
            resetTemporaryPassword(accessToken, userId);
            return new ProvisionedUser(userId, username, true);
        }

        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("email", staffMember.getEmail().trim());
        user.put("enabled", true);
        user.put("emailVerified", true);
        applyNames(user, staffMember.getFullName());
        user.put("attributes", Map.of(
                "staffId", List.of(valueOr(staffMember.getStaffId(), "")),
                "staffRole", List.of(valueOr(staffMember.getStaffRole(), "")),
                "department", List.of(valueOr(staffMember.getDepartment(), ""))
        ));
        user.put("credentials", List.of(Map.of(
                "type", "password",
                "value", TEMPORARY_STAFF_PASSWORD,
                "temporary", true
        )));

        keycloakFeignClient.createUser(accessToken, realm, user);
        Map<String, Object> createdUser = findUser(accessToken, username);
        if (createdUser == null) {
            throw new IllegalStateException("Keycloak user was created but could not be found by username: " + username);
        }

        String userId = String.valueOf(createdUser.get("id"));
        assignGroup(accessToken, userId, staffMember.getStaffRole());
        return new ProvisionedUser(userId, username, false);
    }

    public ProvisionedUser setStaffUserEnabled(StaffMember staffMember, boolean enabled) {
        validateStaffMember(staffMember);
        String username = staffMember.getEmail().trim().toLowerCase(Locale.ROOT);
        String accessToken = bearerToken();
        Map<String, Object> existingUser = findUser(accessToken, username);
        if (existingUser == null || existingUser.get("id") == null) {
            throw new IllegalStateException("Keycloak user does not exist for " + username);
        }

        String userId = String.valueOf(existingUser.get("id"));
        keycloakFeignClient.updateUser(accessToken, realm, userId, Map.of("enabled", enabled));
        return new ProvisionedUser(userId, username, true);
    }

    private String bearerToken() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("grant_type", grantType);
        Map<String, Object> token = keycloakFeignClient.getAccessToken(realm, params);
        Object accessToken = token.get("access_token");
        if (accessToken == null || String.valueOf(accessToken).isBlank()) {
            throw new IllegalStateException("Keycloak access token response did not include access_token");
        }
        return "Bearer " + accessToken;
    }

    private Map<String, Object> findUser(String accessToken, String username) {
        List<Map<String, Object>> users = responseBody(keycloakFeignClient.findUsers(accessToken, realm, null, username, true));
        if (!users.isEmpty()) {
            return users.get(0);
        }
        users = responseBody(keycloakFeignClient.findUsers(accessToken, realm, username, null, true));
        return users.isEmpty() ? null : users.get(0);
    }

    private void assignGroup(String accessToken, String userId, String staffRole) {
        String groupName = groupNameForRole(staffRole);
        if (groupName.isBlank()) {
            return;
        }

        List<Map<String, Object>> groups = keycloakFeignClient.getRealmGroups(accessToken, realm, groupName);
        Map<String, Object> group = groups.stream()
                .filter(candidate -> groupName.equalsIgnoreCase(String.valueOf(candidate.get("name"))))
                .findFirst()
                .orElseGet(() -> groups.stream().findFirst().orElse(null));
        if (group == null || group.get("id") == null) {
            throw new IllegalStateException("Keycloak group not found for staff role: " + groupName);
        }
        keycloakFeignClient.addUserToGroup(accessToken, realm, userId, String.valueOf(group.get("id")));
    }

    private void resetTemporaryPassword(String accessToken, String userId) {
        keycloakFeignClient.resetPassword(accessToken, realm, userId, Map.of(
                "type", "password",
                "value", TEMPORARY_STAFF_PASSWORD,
                "temporary", true
        ));
    }

    private void validateStaffMember(StaffMember staffMember) {
        if (staffMember == null) {
            throw new IllegalArgumentException("Staff member is required");
        }
        if (staffMember.getEmail() == null || staffMember.getEmail().isBlank()) {
            throw new IllegalArgumentException("Staff email is required before creating a Keycloak account");
        }
        if (staffMember.getStaffRole() == null || staffMember.getStaffRole().isBlank()) {
            throw new IllegalArgumentException("Staff access role is required before creating a Keycloak account");
        }
    }

    private void applyNames(Map<String, Object> user, String fullName) {
        String name = valueOr(fullName, "").trim();
        if (name.isBlank()) {
            return;
        }
        String[] parts = name.split("\\s+", 2);
        user.put("firstName", parts[0]);
        if (parts.length > 1) {
            user.put("lastName", parts[1]);
        }
    }

    private String groupNameForRole(String staffRole) {
        String normalizedRole = valueOr(staffRole, "").trim().toUpperCase(Locale.ROOT);
        return switch (normalizedRole) {
            case "RECEPTIONIST" -> "receptionist";
            case "NURSE" -> "nurse";
            case "DOCTOR" -> "doctor";
            case "LABORATORY" -> "laboratory";
            case "RADIOLOGY" -> "radiology";
            case "PHARMACY" -> "pharmacy";
            case "BILLING" -> "billing";
            case "HR" -> "hr";
            case "HOSPITAL_ADMIN" -> "hospital-admin";
            case "DEPARTMENT_MANAGER" -> "department-manager";
            default -> normalizedRole.toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }

    private List<Map<String, Object>> responseBody(org.springframework.http.ResponseEntity<List<Map<String, Object>>> response) {
        return response.getBody() == null ? List.of() : response.getBody();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record ProvisionedUser(String userId, String username, boolean existingAccount) {
    }
}
