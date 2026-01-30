package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import uk.gov.justice.hmpps.kotlin.auth.service.GlobalPrincipalOAuth2AuthorizedClientService
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @param:Value("\${prison.api.url}") private val prisonApiBaseUrl: String,
  @param:Value("\${non-associations.api.url}") private val nonAssociationsApiBaseUrl: String,
  @param:Value("\${prisoner.offender.search.url}") private val prisonOffenderSearchBaseUrl: String,
  @param:Value("\${prisoner-contact.registry.url}") private val prisonContactRegistryUrl: String,
  @param:Value("\${activities.api.url}") private val activitiesApiBaseUrl: String,
  @param:Value("\${api.health.timeout:2s}") val healthTimeout: Duration,
  @param:Value("\${api.timeout:10s}") val apiTimeout: Duration,
) {
  companion object {
    const val CLIENT_REGISTRATION_ID = "hmpps-apis"
  }

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(prisonApiBaseUrl, authorizedClientManager, builder)

  @Bean
  fun nonAssociationsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(nonAssociationsApiBaseUrl, authorizedClientManager, builder)

  @Bean
  fun activitiesApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(activitiesApiBaseUrl, authorizedClientManager, builder)

  @Bean
  fun prisonerOffenderSearchWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(prisonOffenderSearchBaseUrl, authorizedClientManager, builder)

  @Bean
  fun prisonerContactRegistryWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = getWebClient(prisonContactRegistryUrl, authorizedClientManager, builder)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonApiBaseUrl, healthTimeout)

  @Bean
  fun nonAssociationsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(nonAssociationsApiBaseUrl, healthTimeout)

  @Bean
  fun activitiesApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(activitiesApiBaseUrl, healthTimeout)

  @Bean
  fun prisonerOffenderSearchHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonOffenderSearchBaseUrl, healthTimeout)

  @Bean
  fun prisonerContactRegistryHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonContactRegistryUrl, healthTimeout)

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository,
  ): OAuth2AuthorizedClientManager {
    val authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      GlobalPrincipalOAuth2AuthorizedClientService(clientRegistrationRepository),
    )
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }

  private fun getWebClient(
    url: String,
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    url = url,
    registrationId = CLIENT_REGISTRATION_ID,
    timeout = apiTimeout,
  )
}
