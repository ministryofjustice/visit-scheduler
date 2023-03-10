package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter

class AuthAwareTokenConverter : Converter<Jwt, AbstractAuthenticationToken> {
  private val jwtGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>> =
    JwtGrantedAuthoritiesConverter()

  override fun convert(jwt: Jwt): AbstractAuthenticationToken {
    val claims = jwt.claims
    val principal = findPrincipal(claims)
    val authorities = extractAuthorities(jwt)

    // TODO - remove this later - only adding as a temporary thing
    LOG.info("claims information for logged in user - $claims")

    return AuthAwareAuthenticationToken(jwt, principal, authorities)
  }

  private fun findPrincipal(claims: Map<String, Any?>): String {
    return if (claims.containsKey(CLAIM_USERNAME)) {
      claims[CLAIM_USERNAME] as String
    } else if (claims.containsKey(CLAIM_USER_ID)) {
      claims[CLAIM_USER_ID] as String
    } else {
      claims[CLAIM_CLIENT_ID] as String
    }
  }

  private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
    val authorities = mutableListOf<GrantedAuthority>().apply { addAll(jwtGrantedAuthoritiesConverter.convert(jwt)!!) }
    if (jwt.claims.containsKey(CLAIM_AUTHORITY)) {
      @Suppress("UNCHECKED_CAST")
      val claimAuthorities = (jwt.claims[CLAIM_AUTHORITY] as Collection<String>).toList()
      authorities.addAll(claimAuthorities.map(::SimpleGrantedAuthority))
    }
    return authorities.toSet()
  }

  companion object {
    const val CLAIM_USERNAME = "user_name"
    const val CLAIM_USER_ID = "user_id"
    const val CLAIM_CLIENT_ID = "client_id"
    const val CLAIM_AUTHORITY = "authorities"
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

class AuthAwareAuthenticationToken(
  jwt: Jwt,
  private val aPrincipal: String,
  authorities: Collection<GrantedAuthority>
) : JwtAuthenticationToken(jwt, authorities) {
  override fun getPrincipal(): String {
    return aPrincipal
  }
}
