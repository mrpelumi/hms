package com.elegax.hms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
		// Authorize requests
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/", "/static/**", "/login").permitAll()
						.anyRequest().authenticated()
				)
				// OAuth2 login (Keycloak web login)
				.oauth2Login(Customizer.withDefaults())
				// Logout - use OIDC end session endpoint when available
				.logout(logout -> logout
						.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID")
				);

		return http.build();
	}

	@Bean
	public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
		OidcClientInitiatedLogoutSuccessHandler successHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
		// redirect back to application base URL after logout
		successHandler.setPostLogoutRedirectUri("{baseUrl}/");
		return successHandler;
	}

}
