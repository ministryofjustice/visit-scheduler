package uk.gov.justice.digital.hmpps.visitscheduler.config

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.opentelemetry.api.trace.Span
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.text.ParseException

@Configuration
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isNotBlank('\${applicationinsights.connection.string:}')")
class ClientTrackingConfiguration(private val clientTrackingInterceptor: ClientTrackingInterceptor) : WebMvcConfigurer {
  override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(clientTrackingInterceptor).addPathPatterns("/**")
  }
}

@Configuration
class ClientTrackingInterceptor : HandlerInterceptor {

  companion object {
    private val LOG = LoggerFactory.getLogger(ClientTrackingInterceptor::class.java)
  }

  override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
    val token = request.getHeader(HttpHeaders.AUTHORIZATION)
    if (token?.startsWith("Bearer ") == true) {
      try {
        val jwtBody = getClaimsFromJWT(token)
        val user = jwtBody.getClaim("user_name")?.toString()
        val currentSpan = getCurrentSpan()
        user?.run {
          currentSpan.setAttribute("username", this) // username in customDimensions
          currentSpan.setAttribute("enduser.id", this) // user_Id at the top level of the request
        }
        currentSpan.setAttribute("clientId", jwtBody.getClaim("client_id").toString())
      } catch (e: ParseException) {
        LOG.warn("problem decoding jwt public key for application insights", e)
      }
    }
    return true
  }

  fun getCurrentSpan(): Span = Span.current()

  @Throws(ParseException::class)
  private fun getClaimsFromJWT(token: String): JWTClaimsSet = SignedJWT.parse(token.replace("Bearer ", "")).jwtClaimsSet
}
