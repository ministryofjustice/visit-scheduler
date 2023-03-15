package uk.gov.justice.digital.hmpps.visitscheduler.config

import io.opentelemetry.api.trace.Span
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.visitscheduler.helper.JwtAuthHelper

@Import(JwtAuthHelper::class, ClientTrackingInterceptor::class, ClientTrackingConfiguration::class)
@ContextConfiguration(initializers = [ConfigDataApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class ClientTrackingConfigurationTest {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var clientTrackingInterceptor: ClientTrackingInterceptor

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var jwtAuthHelper: JwtAuthHelper

  @BeforeEach
  fun setup() {
  }

  @AfterEach
  fun tearDown() {
  }

  @Test
  fun shouldAddClientIdAndUserNameToInsightTelemetry() {
    // Given
    val token = jwtAuthHelper.createJwt("bob")
    val req = MockHttpServletRequest()
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
    val res = MockHttpServletResponse()

    val clientTrackingInterceptorSpy = spy(clientTrackingInterceptor)
    val mockSpan = spy(Span.current())
    whenever(clientTrackingInterceptorSpy.getCurrentSpan()).thenReturn(mockSpan)

    // When
    clientTrackingInterceptorSpy.preHandle(req, res, "null")

    // Then
    verify(mockSpan, times(1)).setAttribute("username", "bob")
    verify(mockSpan, times(1)).setAttribute("enduser.id", "bob")
    verify(mockSpan, times(1)).setAttribute("clientId", "visit-scheduler-client")
  }

  @Test
  fun shouldAddOnlyClientIdIfUsernameNullToInsightTelemetry() {
    // Given
    val token = jwtAuthHelper.createJwt(null)
    val req = MockHttpServletRequest()
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
    val res = MockHttpServletResponse()

    val clientTrackingInterceptorSpy = spy(clientTrackingInterceptor)
    val mockSpan = spy(Span.current())
    whenever(clientTrackingInterceptorSpy.getCurrentSpan()).thenReturn(mockSpan)

    // When
    clientTrackingInterceptorSpy.preHandle(req, res, "null")

    // Then
    verify(mockSpan, times(0)).setAttribute("username", "bob")
    verify(mockSpan, times(0)).setAttribute("enduser.id", "bob")
    verify(mockSpan, times(1)).setAttribute("clientId", "visit-scheduler-client")
  }
}
