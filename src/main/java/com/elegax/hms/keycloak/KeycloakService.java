package com.elegax.hms.keycloak;

import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.entity.Patient;
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
    public static final String TEMPORARY_PATIENT_PASSWORD = "1234";

    private final KeycloakFeignClient keycloakFeignClient;

    @Value("${keycloak.realm:hmsRealm}")
    private String realm;

    @Value("${keycloak.patient.realm:hms-patient}")
    private String patientRealm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.patient.client-id:${spring.security.oauth2.client.registration.keycloak-patient.client-id:hms-patient-client}}")
    private String patientClientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.patient.client-secret:${spring.security.oauth2.client.registration.keycloak-patient.client-secret:}}")
    private String patientClientSecret;

    @Value("${keycloak.grant-type:client_credentials}")
    private String grantType;

    public KeycloakService(KeycloakFeignClient keycloakFeignClient) {
        this.keycloakFeignClient = keycloakFeignClient;
    }

    public ProvisionedUser provisionStaffUser(StaffMember staffMember) {
        validateStaffMember(staffMember);

        String username = staffMember.getEmail().trim().toLowerCase(Locale.ROOT);
        String accessToken = bearerToken(realm, clientId, clientSecret);
        Map<String, Object> existingUser = findUser(accessToken, realm, username);
        if (existingUser != null) {
            String userId = String.valueOf(existingUser.get("id"));
            keycloakFeignClient.updateUser(accessToken, realm, userId, Map.of("enabled", true));
            assignGroup(accessToken, realm, userId, staffMember.getStaffRole());
            resetTemporaryPassword(accessToken, realm, userId);
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
        Map<String, Object> createdUser = findUser(accessToken, realm, username);
        if (createdUser == null) {
            throw new IllegalStateException("Keycloak user was created but could not be found by username: " + username);
        }

        String userId = String.valueOf(createdUser.get("id"));
        assignGroup(accessToken, realm, userId, staffMember.getStaffRole());
        return new ProvisionedUser(userId, username, false);
    }

    public ProvisionedUser setStaffUserEnabled(StaffMember staffMember, boolean enabled) {
        validateStaffMember(staffMember);
        String username = staffMember.getEmail().trim().toLowerCase(Locale.ROOT);
        String accessToken = bearerToken(realm, clientId, clientSecret);
        Map<String, Object> existingUser = findUser(accessToken, realm, username);
        if (existingUser == null || existingUser.get("id") == null) {
            throw new IllegalStateException("Keycloak user does not exist for " + username);
        }

        String userId = String.valueOf(existingUser.get("id"));
        keycloakFeignClient.updateUser(accessToken, realm, userId, Map.of("enabled", enabled));
        return new ProvisionedUser(userId, username, true);
    }

    public ProvisionedUser provisionPatientUser(Patient patient) {
        validatePatient(patient);

        String username = patient.getEmailAddress().trim().toLowerCase(Locale.ROOT);
        String accessToken = bearerToken(patientRealm, patientClientId, patientClientSecret);
        Map<String, Object> existingUser = findUser(accessToken, patientRealm, username);
        if (existingUser != null) {
            String userId = String.valueOf(existingUser.get("id"));
            keycloakFeignClient.updateUser(accessToken, patientRealm, userId, userUpdateForPatient(patient, true));
            assignGroup(accessToken, patientRealm, userId, "PATIENT");
            resetTemporaryPassword(accessToken, patientRealm, userId, TEMPORARY_PATIENT_PASSWORD);
            return new ProvisionedUser(userId, username, true);
        }

        Map<String, Object> user = userUpdateForPatient(patient, true);
        user.put("username", username);
        user.put("email", patient.getEmailAddress().trim());
        user.put("emailVerified", true);
        applyNames(user, patient.getFullName());
        user.put("credentials", List.of(Map.of(
                "type", "password",
                "value", TEMPORARY_PATIENT_PASSWORD,
                "temporary", true
        )));

        keycloakFeignClient.createUser(accessToken, patientRealm, user);
        Map<String, Object> createdUser = findUser(accessToken, patientRealm, username);
        if (createdUser == null) {
            throw new IllegalStateException("Keycloak patient user was created but could not be found by username: " + username);
        }

        String userId = String.valueOf(createdUser.get("id"));
        assignGroup(accessToken, patientRealm, userId, "PATIENT");
        return new ProvisionedUser(userId, username, false);
    }

    private String bearerToken(String tokenRealm, String tokenClientId, String tokenClientSecret) {
        if (tokenRealm == null || tokenRealm.isBlank()) {
            throw new IllegalStateException("Keycloak realm is not configured");
        }
        if (tokenClientId == null || tokenClientId.isBlank()) {
            throw new IllegalStateException("Keycloak client id is not configured for realm " + tokenRealm);
        }
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", tokenClientId);
        if (tokenClientSecret != null && !tokenClientSecret.isBlank()) {
            params.add("client_secret", tokenClientSecret);
        }
        params.add("grant_type", grantType);
        Map<String, Object> token = keycloakFeignClient.getAccessToken(tokenRealm, params);
        Object accessToken = token.get("access_token");
        if (accessToken == null || String.valueOf(accessToken).isBlank()) {
            throw new IllegalStateException("Keycloak access token response did not include access_token");
        }
        return "Bearer " + accessToken;
    }

    private Map<String, Object> findUser(String accessToken, String targetRealm, String username) {
        List<Map<String, Object>> users = responseBody(keycloakFeignClient.findUsers(accessToken, targetRealm, null, username, true));
        if (!users.isEmpty()) {
            return users.get(0);
        }
        users = responseBody(keycloakFeignClient.findUsers(accessToken, targetRealm, username, null, true));
        return users.isEmpty() ? null : users.get(0);
    }

    private void assignGroup(String accessToken, String targetRealm, String userId, String staffRole) {
        String groupName = groupNameForRole(staffRole);
        if (groupName.isBlank()) {
            return;
        }

        List<Map<String, Object>> groups = keycloakFeignClient.getRealmGroups(accessToken, targetRealm, groupName);
        Map<String, Object> group = groups.stream()
                .filter(candidate -> groupName.equalsIgnoreCase(String.valueOf(candidate.get("name"))))
                .findFirst()
                .orElseGet(() -> groups.stream().findFirst().orElse(null));
        if (group == null || group.get("id") == null) {
            throw new IllegalStateException("Keycloak group not found for staff role: " + groupName);
        }
        keycloakFeignClient.addUserToGroup(accessToken, targetRealm, userId, String.valueOf(group.get("id")));
    }

    private void resetTemporaryPassword(String accessToken, String targetRealm, String userId) {
        resetTemporaryPassword(accessToken, targetRealm, userId, TEMPORARY_STAFF_PASSWORD);
    }

    private void resetTemporaryPassword(String accessToken, String targetRealm, String userId, String password) {
        keycloakFeignClient.resetPassword(accessToken, targetRealm, userId, Map.of(
                "type", "password",
                "value", password,
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

    private void validatePatient(Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("Patient is required");
        }
        if (patient.getEmailAddress() == null || patient.getEmailAddress().isBlank()) {
            throw new IllegalArgumentException("Patient email is required before creating a portal account");
        }
        if (patient.getPatientId() == null || patient.getPatientId().isBlank()) {
            throw new IllegalArgumentException("Patient ID is required before creating a portal account");
        }
    }

    private Map<String, Object> userUpdateForPatient(Patient patient, boolean enabled) {
        Map<String, Object> user = new HashMap<>();
        user.put("enabled", enabled);
        user.put("email", patient.getEmailAddress().trim());
        user.put("emailVerified", true);
        applyNames(user, patient.getFullName());
        user.put("attributes", Map.of(
                "patientId", List.of(valueOr(patient.getPatientId(), "")),
                "patientRecordId", List.of(patient.getId() == null ? "" : String.valueOf(patient.getId())),
                "patientPhone", List.of(valueOr(patient.getPhoneNumber(), ""))
        ));
        return user;
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
            case "PATIENT" -> "patient";
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
