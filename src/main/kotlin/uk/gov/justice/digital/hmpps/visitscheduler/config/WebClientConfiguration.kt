package uk.gov.justice.digital.hmpps.visitscheduler.config

import io.netty.resolver.DefaultAddressResolverGroup
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration.ofSeconds

@Configuration
class WebClientConfiguration(
  @Value("\${prison.api.url}") private val prisonApiBaseUrl: String,
  @Value("\${non-associations.api.url}") private val nonAssociationsApiBaseUrl: String,
  @Value("\${prisoner.offender.search.url}") private val prisonOffenderSearchBaseUrl: String,
  @Value("\${prisoner-contact.registry.url}") private val prisonContactRegistryUrl: String,
  @Value("\${activities.api.url}") private val activitiesApiBaseUrl: String,
) {
  companion object {
    const val CLIENT_REGISTRATION_ID = "hmpps-apis"
  }

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = getOauth2Client(authorizedClientManager, CLIENT_REGISTRATION_ID)
    return getWebClient(prisonApiBaseUrl, oauth2Client)
  }

  @Bean
  fun nonAssociationsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = getOauth2Client(authorizedClientManager, CLIENT_REGISTRATION_ID)
    return getWebClient(nonAssociationsApiBaseUrl, oauth2Client)
  }

  @Bean
  fun activitiesApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = getOauth2Client(authorizedClientManager, CLIENT_REGISTRATION_ID)
    return getWebClient(activitiesApiBaseUrl, oauth2Client)
  }

  @Bean
  fun prisonerOffenderSearchWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = getOauth2Client(authorizedClientManager, CLIENT_REGISTRATION_ID)
    return getWebClient(prisonOffenderSearchBaseUrl, oauth2Client)
  }

  @Bean
  fun prisonerContactRegistryWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = getOauth2Client(authorizedClientManager, CLIENT_REGISTRATION_ID)
    return getWebClient(prisonContactRegistryUrl, oauth2Client)
  }

  @Bean
  fun prisonApiHealthWebClient(): WebClient = WebClient.builder().baseUrl(prisonApiBaseUrl).build()

  @Bean
  fun prisonOffenderSearchHealthWebClient(): WebClient = WebClient.builder().baseUrl(prisonOffenderSearchBaseUrl).build()

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository?,
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService?,
  ): OAuth2AuthorizedClientManager? {
    val authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager =
      AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService)
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }

  private fun getOauth2Client(authorizedClientManager: OAuth2AuthorizedClientManager, clientRegistrationId: String): ServletOAuth2AuthorizedClientExchangeFilterFunction {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId(clientRegistrationId)
    return oauth2Client
  }

  private fun getExchangeStrategies(): ExchangeStrategies = ExchangeStrategies.builder()
    .codecs { configurer: ClientCodecConfigurer -> configurer.defaultCodecs().maxInMemorySize(-1) }
    .build()

  private fun getWebClient(baseUrl: String, oauth2Client: ServletOAuth2AuthorizedClientExchangeFilterFunction): WebClient {
    val provider = ConnectionProvider.builder("custom")
      .maxConnections(500)
      .maxIdleTime(ofSeconds(20))
      .maxLifeTime(ofSeconds(60))
      .pendingAcquireTimeout(ofSeconds(60))
      .evictInBackground(ofSeconds(120))
      .build()

    return WebClient.builder()
      .baseUrl(baseUrl)
      .clientConnector(
        ReactorClientHttpConnector(
          HttpClient.create(provider)
            .resolver(DefaultAddressResolverGroup.INSTANCE)
            .compress(true)
            .responseTimeout(ofSeconds(30)),
        ),
      )
      .apply(oauth2Client.oauth2Configuration())
      .exchangeStrategies(getExchangeStrategies())
      .build()
  }
}
