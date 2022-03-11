package uk.gov.justice.digital.hmpps.visitscheduler.config

import com.microsoft.applicationinsights.web.internal.ThreadContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.text.ParseException
import java.util.Optional
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isNotBlank('\${applicationinsights.connection.string:}')")
class ClientTrackingConfiguration(private val clientTrackingInterceptor: ClientTrackingInterceptor) : WebMvcConfigurer {
  override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(clientTrackingInterceptor).addPathPatterns("/**")
  }
}

@Configuration
class ClientTrackingInterceptor : HandlerInterceptor {
  override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
    val token = request.getHeader(HttpHeaders.AUTHORIZATION)
    val bearer = "Bearer "
    if (StringUtils.startsWithIgnoreCase(token, bearer)) {
      try {
        val jwtBody = getClaimsFromJWT(token)
        val properties = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
        val user = Optional.ofNullable(jwtBody.getClaim("user_name"))
        user.map { it.toString() }.ifPresent { properties["username"] = it }
        properties["clientId"] = jwtBody.getClaim("client_id").toString()
      } catch (e: ParseException) {
        log.warn("problem decoding jwt public key for application insights", e)
      }
    }
    return true
  }

  @Throws(ParseException::class)
  private fun getClaimsFromJWT(token: String): JWTClaimsSet =
    SignedJWT.parse(token.replace("Bearer ", "")).jwtClaimsSet

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
