package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class AuthenticationHelperService {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val UNEXPECTED_PRINCIPAL = "NOT_KNOWN"
  }

  val currentUserName: String
    get() {
      val userPrincipal = userPrincipal

      return if (userPrincipal is String) userPrincipal else {
        LOG.info("unexpected user principal - $userPrincipal")
        UNEXPECTED_PRINCIPAL
      }
    }

  private val userPrincipal: Any?
    get() {
      val authentication: Authentication = SecurityContextHolder.getContext().authentication
      return authentication.principal
    }
}
