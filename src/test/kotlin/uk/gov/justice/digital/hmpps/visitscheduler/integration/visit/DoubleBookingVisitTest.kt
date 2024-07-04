package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import java.util.concurrent.CyclicBarrier

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class DoubleBookingVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit
  private lateinit var reservedApplication: Application

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false)
    applicationEntityHelper.createContact(application = reservedApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = reservedApplication, nomisPersonId = 321L, visitContact = true)
    reservedApplication = applicationEntityHelper.save(reservedApplication)

    stubApplicationCreationHappyPathCalls(
      prisonerId = reservedApplication.prisonerId,
      prisonCode = reservedApplication.prison.code,
    )
  }

  @Test
  fun `Book multiple visits using same application reference results in the same booking returned`() {
    // Given

    val applicationReference = reservedApplication.reference

    val threadCount = 10
    val responseSpecs: MutableList<ResponseSpec> = mutableListOf()
    val threads: MutableList<Thread> = mutableListOf()
    val visitDtoList: MutableList<VisitDto> = mutableListOf()

    val gateKeeper = CyclicBarrier(threadCount)

    for (index in 1..<threadCount) {
      threads.add(
        Thread {
          gateKeeper.await()
          System.err.println("Start Thread : $index : " + System.currentTimeMillis())
          val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)
          responseSpecs.add(responseSpec)
        },
      )
    }

    // When

    threads.forEach { it.start() }
    gateKeeper.await()
    threads.forEach { it.join(500) }

    // Then

    assertThat(responseSpecs.size).isEqualTo(threadCount - 1)
    responseSpecs.forEach {
      it.expectStatus().isOk
      visitDtoList.add(getVisitDto(it))
    }

    var lastVisitDto: VisitDto? = null
    visitDtoList.forEach { currentVisitDto ->
      lastVisitDto?.let {
        assertThat(it.reference).isEqualTo(currentVisitDto.reference)
        assertThat(it.applicationReference).isEqualTo(currentVisitDto.applicationReference)
      }
      lastVisitDto = currentVisitDto
    }
  }

  @Test
  fun `When booking a visit a capacity exception rolls back to incomplete`() {
    // Given
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", openCapacity = 0)
    val reservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false)
    val applicationReference = reservedApplication.reference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    assertHelper.assertBookingCapacityError(responseSpec, reservedApplication)

    val application = testApplicationRepository.findByApplicationReference(applicationReference)
    assertThat(application).isNotNull
    application?.let {
      assertThat(application.completed).isFalse()
      assertThat(application.visit).isNull()
    }
  }
}
