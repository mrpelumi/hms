package com.elegax.hms.authentication;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationManager {

    public Object get(String fieldName){
        Authentication authentication= SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null){
            return null;
        }
        if(authentication.getPrincipal() instanceof DefaultOidcUser defaultOidcUser){
            return defaultOidcUser.getClaims().get(fieldName);
        } else if (authentication.getPrincipal() instanceof Jwt defaultJwtUser) {
            return defaultJwtUser.getClaims().get(fieldName);
        } else  {
            throw new IllegalStateException("Unknown Authentication Principal Type");
        }
    }


    public Boolean isReceptionist(){
        List<String> groups = (List<String>) get("group");
        if(groups==null)
            return false;

        return groups
                .stream()
                .anyMatch(group->"/receptionist".equalsIgnoreCase(group));
    }

}
