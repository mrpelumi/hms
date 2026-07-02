package com.elegax.hms.authentication;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service("userGroupManager")
public class AuthenticationManager {

    public Object get(String fieldName){
        Authentication authentication = getAuthentication();
        if(authentication==null){
            return null;
        }
        if(authentication.getPrincipal() instanceof DefaultOidcUser defaultOidcUser){
            return defaultOidcUser.getClaims().get(fieldName);
        } else if (authentication.getPrincipal() instanceof Jwt defaultJwtUser) {
            return defaultJwtUser.getClaims().get(fieldName);
        } else {
            return null;
        }
    }


    public Boolean isReceptionist(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "receptionist", "reception"));
    }

    public Boolean isDoctor(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "doctor"));
    }

    public Boolean isNurse(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "nurse", "nursing"));
    }

    public Boolean isLaboratory(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "laboratory", "lab", "medical laboratory"));
    }

    public Boolean isRadiology(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "radiology", "radiographer", "imaging"));
    }

    public Boolean isPharmacy(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "pharmacy", "pharmacist"));
    }

    public Boolean isBilling(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "billing", "finance", "cashier"));
    }

    public Boolean isHr(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "hr", "human resources", "human-resource", "human_resources"));
    }

    public Boolean isHospitalAdmin(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "hospital admin", "hospital-admin", "hospital_admin", "hospital administrator", "hospital-administrator", "hospital_administrator"));
    }

    public Boolean isDepartmentManager(){
        return getGroups()
                .stream()
                .anyMatch(group -> hasRole(group, "department manager", "department-manager", "department_manager",
                        "department admin", "department-admin", "department_admin",
                        "department administrator", "department-administrator", "department_administrator",
                        "dept manager", "dept-manager", "dept admin", "dept-admin"));
    }

    public String getUsername() {
        return firstNonBlank(get("preferred_username"), get("email"), get("username"), get("sub"));
    }

    public String getDisplayName() {
        String fullName = firstNonBlank(get("name"));
        if (!fullName.isBlank()) {
            return fullName;
        }

        String givenName = firstNonBlank(get("given_name"));
        String familyName = firstNonBlank(get("family_name"));
        fullName = (givenName + " " + familyName).trim();
        return fullName.isBlank() ? getUsername() : fullName;
    }

    private List<String> getGroups() {
        return Stream.concat(
                        Stream.of(get("group"), get("groups"), get("role"), get("roles"), get("authorities"),
                                        get("realm_access"), get("resource_access"))
                                .flatMap(this::groupValues),
                        authorityValues()
                )
                .flatMap(this::splitGroupValue)
                .map(String::trim)
                .filter(group -> !group.isBlank())
                .toList();
    }

    private Stream<String> authorityValues() {
        Authentication authentication = getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Stream.empty();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority);
    }

    private Stream<String> groupValues(Object groups) {
        if (groups instanceof Collection<?> collection) {
            return collection.stream().flatMap(this::groupValues);
        }
        if (groups instanceof Map<?, ?> map) {
            return map.values().stream().flatMap(this::groupValues);
        }
        if (groups instanceof String group) {
            return Stream.of(group);
        }
        if (groups != null) {
            return Stream.of(String.valueOf(groups));
        }
        return Stream.empty();
    }

    private Stream<String> splitGroupValue(String group) {
        return Stream.of(group.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split("[,;]+"));
    }

    private boolean hasRole(String group, String... acceptedRoles) {
        String normalizedGroup = normalizeGroup(group);
        return Stream.of(acceptedRoles)
                .map(this::normalizeGroup)
                .anyMatch(role -> normalizedGroup.equals(role) || normalizedGroup.equals(role + "s"));
    }

    private String normalizeGroup(String group) {
        String normalizedGroup = group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
        if (normalizedGroup.startsWith("/")) {
            normalizedGroup = normalizedGroup.substring(1);
        }
        int slashIndex = normalizedGroup.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < normalizedGroup.length() - 1) {
            normalizedGroup = normalizedGroup.substring(slashIndex + 1);
        }
        if (normalizedGroup.startsWith("role_")) {
            normalizedGroup = normalizedGroup.substring(5);
        }
        if (normalizedGroup.startsWith("scope_")) {
            normalizedGroup = normalizedGroup.substring(6);
        }
        normalizedGroup = normalizedGroup.replace("-", "").replace("_", "").replace(" ", "");
        return normalizedGroup;
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String firstNonBlank(Object... values) {
        return Stream.of(values)
                .filter(value -> value != null && !String.valueOf(value).isBlank())
                .map(String::valueOf)
                .findFirst()
                .orElse("");
    }

}
