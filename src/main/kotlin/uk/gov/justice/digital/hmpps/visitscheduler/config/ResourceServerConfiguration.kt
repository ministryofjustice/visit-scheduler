package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
class ResourceServerConfiguration : WebSecurityConfigurerAdapter() {

  override fun configure(http: HttpSecurity) {
    http
      .sessionManagement()
      .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      .and().csrf().disable()
      .authorizeRequests { auth ->
        auth.antMatchers(
          "/health/**", "/info",
          "/v3/api-docs/**",
          "/swagger-ui/**", "/swagger-ui.html",
          "/swagger-resources",
          "/swagger-resources/configuration/ui",
          "/swagger-resources/configuration/security",
          "/webjars/**", "/favicon.ico", "/csrf",
          "/h2-console/**",
        ).permitAll().anyRequest().authenticated()
      }.oauth2ResourceServer().jwt().jwtAuthenticationConverter(AuthAwareTokenConverter())
  }
}
