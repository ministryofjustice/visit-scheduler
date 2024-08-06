package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.WebTestClient.Builder
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.scheduler.Schedulers

@Component
class WebTestClientConfig : WebTestClientBuilderCustomizer {
  // This is to allow calls to web test client to happen at the same time rather than serially
  // more closely resembling how requests would happen in real world use of this API.
  override fun customize(builder: Builder) {
    builder.filter { clientRequest: ClientRequest, next: ExchangeFunction ->
      next.exchange(
        clientRequest,
      ).subscribeOn(Schedulers.parallel())
    }
  }
}
