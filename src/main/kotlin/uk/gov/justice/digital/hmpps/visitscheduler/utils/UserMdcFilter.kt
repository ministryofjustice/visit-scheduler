package uk.gov.justice.digital.hmpps.visitscheduler.utils

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.io.IOException

@Component
@Order(1)
class UserMdcFilter @Autowired constructor() : Filter {

  override fun init(filterConfig: FilterConfig) {
    // Initialise - no functionality
  }

  override fun destroy() {
    // Destroy - no functionality
  }

  @Throws(IOException::class, ServletException::class)
  override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    val currentUsername = getUserName()
    try {
      currentUsername?.let { MDC.put(USER_ID_HEADER, currentUsername) }
      chain.doFilter(request, response)
    } finally {
      currentUsername?.let { MDC.remove(USER_ID_HEADER) }
    }
  }

  private fun getUserName(): String? {
    return when (val principle = SecurityContextHolder.getContext().authentication.principal) {
      is String -> { principle }
      is UserDetails -> { principle.username }
      is Map<*, *> -> { principle[USERNAME_ATTR] as String? }
      else -> { null }
    }
  }

  companion object {
    const val USER_ID_HEADER = "userId"
    const val USERNAME_ATTR = "username"
  }
}
