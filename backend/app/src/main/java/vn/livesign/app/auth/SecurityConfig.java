package vn.livesign.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import vn.livesign.app.web.ApiError;
import vn.livesign.app.web.RequestIdFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

  static final String ROLE_CLAIM = "role";

  @Bean
  SecurityFilterChain api(HttpSecurity http, ObjectMapper json) throws Exception {
    JwtAuthenticationConverter roles = new JwtAuthenticationConverter();
    roles.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString(ROLE_CLAIM);
          return role == null
              ? List.of()
              : List.of(new SimpleGrantedAuthority("ROLE_" + Role.fromDb(role).name()));
        });

    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole(Role.ADMIN.name())
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(roles))
                    .authenticationEntryPoint(
                        (req, res, e) ->
                            write(
                                json,
                                req,
                                res,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED",
                                "Cần đăng nhập"))
                    .accessDeniedHandler(
                        (req, res, e) ->
                            write(
                                json,
                                req,
                                res,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "Thiếu vai trò")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            write(
                                json,
                                req,
                                res,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED",
                                "Cần đăng nhập"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            write(
                                json,
                                req,
                                res,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "Thiếu vai trò")))
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtEncoder jwtEncoder(JwtProperties props) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(props.key()));
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties props) {
    return NimbusJwtDecoder.withSecretKey(props.key()).macAlgorithm(MacAlgorithm.HS256).build();
  }

  private static void write(
      ObjectMapper json,
      HttpServletRequest req,
      HttpServletResponse res,
      HttpStatus status,
      String code,
      String message)
      throws IOException {
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    res.setCharacterEncoding("UTF-8");
    json.writeValue(
        res.getOutputStream(), ApiError.of(code, message, List.of(), RequestIdFilter.of(req)));
  }
}
