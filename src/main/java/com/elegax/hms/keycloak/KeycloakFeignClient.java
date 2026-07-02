package com.elegax.hms.keycloak;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "KeycloakFeignClient", url = "${keycloak.base-url}")
public interface KeycloakFeignClient {

    @PostMapping(path = "/realms/{realm}/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Map<String, Object> getAccessToken(@PathVariable String realm,
                                       @RequestBody MultiValueMap<String, String> params);

    @PostMapping("/admin/realms/{realm}/users")
    ResponseEntity<Void> createUser(@RequestHeader("Authorization") String authorizationHeader,
                                    @PathVariable String realm,
                                    @RequestBody Map<String, Object> user);

    @GetMapping("/admin/realms/{realm}/users")
    ResponseEntity<List<Map<String, Object>>> findUsers(@RequestHeader("Authorization") String authorizationHeader,
                                                        @PathVariable String realm,
                                                        @RequestParam(name = "email", required = false) String email,
                                                        @RequestParam(name = "username", required = false) String username,
                                                        @RequestParam(name = "exact", defaultValue = "true") boolean exact);

    @GetMapping("/admin/realms/{realm}/groups")
    List<Map<String, Object>> getRealmGroups(@RequestHeader("Authorization") String authorizationHeader,
                                             @PathVariable String realm,
                                             @RequestParam(name = "search", required = false) String search);

    @PutMapping("/admin/realms/{realm}/users/{userId}/groups/{groupId}")
    void addUserToGroup(@RequestHeader("Authorization") String authorizationHeader,
                        @PathVariable String realm,
                        @PathVariable String userId,
                        @PathVariable String groupId);

    @PutMapping("/admin/realms/{realm}/users/{userId}/reset-password")
    void resetPassword(@RequestHeader("Authorization") String authorizationHeader,
                       @PathVariable String realm,
                       @PathVariable String userId,
                       @RequestBody Map<String, Object> credentials);

    @PutMapping("/admin/realms/{realm}/users/{userId}")
    void updateUser(@RequestHeader("Authorization") String authorizationHeader,
                    @PathVariable String realm,
                    @PathVariable String userId,
                    @RequestBody Map<String, Object> user);
}
