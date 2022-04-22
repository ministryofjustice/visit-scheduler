package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateSupportOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitorOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitContactCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitNoteCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitSupportCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitVisitorCreator
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

class VisitControllerTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  private lateinit var legacyDataRepository: LegacyDataRepository

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @DisplayName("POST /visits")
  @Nested
  inner class CreateVisit {

    fun createVisitRequest(leadPersonId: Long? = null): CreateVisitRequestDto {
      return CreateVisitRequestDto(
        prisonId = "MDI",
        prisonerId = "FF0000FF",
        visitRoom = "A1",
        visitType = VisitType.SOCIAL,
        startTimestamp = visitTime,
        endTimestamp = visitTime.plusHours(1),
        visitStatus = VisitStatus.RESERVED,
        visitRestriction = VisitRestriction.OPEN,
        visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
        visitors = listOf(CreateVisitorOnVisitRequestDto(123)),
        visitorSupport = listOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
        visitNotes = listOf(
          VisitNoteDto(type = VISITOR_CONCERN, "A visit concern"),
          VisitNoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
          VisitNoteDto(type = VISIT_COMMENT, "A visit comment"),
          VisitNoteDto(type = STATUS_CHANGED_REASON, "Status has changed")
        ),
        legacyData = leadPersonId?.let { CreateLegacyDataRequestDto(leadPersonId) }
      )
    }

    @Test
    fun `create visit`() {
      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            createVisitRequest()
          )
        )
        .exchange()
        .expectStatus().isCreated

      webTestClient.get().uri("/visits?prisonerId=FF0000FF")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].reference").isNotEmpty
        .jsonPath("$[0].prisonId").isEqualTo("MDI")
        .jsonPath("$[0].prisonerId").isEqualTo("FF0000FF")
        .jsonPath("$[0].visitRoom").isEqualTo("A1")
        .jsonPath("$[0].visitType").isEqualTo(VisitType.SOCIAL.name)
        .jsonPath("$[0].startTimestamp").isEqualTo(visitTime.toString())
        .jsonPath("$[0].endTimestamp").isEqualTo(visitTime.plusHours(1).toString())
        .jsonPath("$[0].visitStatus").isEqualTo(VisitStatus.RESERVED.name)
        .jsonPath("$[0].visitRestriction").isEqualTo(VisitRestriction.OPEN.name)
        .jsonPath("$[0].visitContact.name").isNotEmpty
        .jsonPath("$[0].visitContact.name").isEqualTo("John Smith")
        .jsonPath("$[0].visitContact.telephone").isEqualTo("01234 567890")
        .jsonPath("$[0].visitors.length()").isEqualTo(1)
        .jsonPath("$[0].visitors[0].nomisPersonId").isEqualTo(123)
        .jsonPath("$[0].visitorSupport.length()").isEqualTo(1)
        .jsonPath("$[0].visitorSupport[0].type").isEqualTo("OTHER")
        .jsonPath("$[0].visitorSupport[0].text").isEqualTo("Some Text")
        .jsonPath("$[0].visitNotes.length()").isEqualTo(4)
        .jsonPath("$[0].visitNotes[0].type").isEqualTo("VISITOR_CONCERN")
        .jsonPath("$[0].visitNotes[1].type").isEqualTo("VISIT_OUTCOMES")
        .jsonPath("$[0].visitNotes[2].type").isEqualTo("VISIT_COMMENT")
        .jsonPath("$[0].visitNotes[3].type").isEqualTo("STATUS_CHANGED_REASON")
        .jsonPath("$[0].visitNotes[0].text").isEqualTo("A visit concern")
        .jsonPath("$[0].visitNotes[1].text").isEqualTo("A visit outcome")
        .jsonPath("$[0].visitNotes[2].text").isEqualTo("A visit comment")
        .jsonPath("$[0].visitNotes[3].text").isEqualTo("Status has changed")
    }

    @Test
    fun `create visit with legacy data`() {

      // Arrange
      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            createVisitRequest(leadPersonId = 123)
          )
        )
        .exchange()
        .expectStatus().isCreated

      // Act
      val webResponse = webTestClient.get().uri("/visits?prisonerId=FF0000FF")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()

      // Assert

      var reference = ""

      webResponse
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$[0].reference")
        .value<String> { json -> reference = json }

      var legacyData: LegacyData? = null
      val visit = visitRepository.findByReference(reference)
      visit?.let {
        legacyData = legacyDataRepository.findByVisitId(visit.id)
      }

      Assertions.assertThat(visit).isNotNull
      Assertions.assertThat(legacyData).isNotNull
      Assertions.assertThat(legacyData!!.visitId).isEqualTo(visit!!.id)
    }

    @Test
    fun `create visit - duplicates are ignored`() {
      val createVisitRequest = CreateVisitRequestDto(
        prisonerId = "FF0000FF",
        prisonId = "MDI",
        startTimestamp = visitTime,
        endTimestamp = visitTime.plusHours(1),
        visitType = VisitType.SOCIAL,
        visitStatus = VisitStatus.RESERVED,
        visitRestriction = VisitRestriction.OPEN,
        visitRoom = "A1",
        visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
        visitors = listOf(
          CreateVisitorOnVisitRequestDto(123),
          CreateVisitorOnVisitRequestDto(123)
        ),
        visitorSupport = listOf(
          CreateSupportOnVisitRequestDto("OTHER", "Some Text"),
          CreateSupportOnVisitRequestDto("OTHER", "Some Text")
        )
      )

      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            createVisitRequest
          )
        )
        .exchange()
        .expectStatus().isCreated

      webTestClient.get().uri("/visits?prisonerId=FF0000FF")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].visitors.length()").isEqualTo(1)
        .jsonPath("$[0].visitorSupport.length()").isEqualTo(1)
    }

    @Test
    fun `create visit - invalid support`() {

      val createVisitRequest = CreateVisitRequestDto(
        prisonerId = "FF0000FF",
        prisonId = "MDI",
        startTimestamp = visitTime,
        endTimestamp = visitTime.plusHours(1),
        visitType = VisitType.SOCIAL,
        visitStatus = VisitStatus.RESERVED,
        visitRestriction = VisitRestriction.OPEN,
        visitRoom = "A1",
        visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
        visitorSupport = listOf(CreateSupportOnVisitRequestDto("ANYTHINGWILLDO")),
      )

      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            createVisitRequest
          )
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `create visit - invalid request`() {
      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            mapOf("wrongProperty" to "wrongValue")
          )
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf()))
        .body(
          BodyInserters.fromValue(
            createVisitRequest(leadPersonId = null)
          )
        )
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `unauthorised when no token`() {
      webTestClient.post().uri("/visits")
        .body(
          BodyInserters.fromValue(
            createVisitRequest(leadPersonId = null)
          )
        )
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  @DisplayName("GET /visits")
  @Nested
  inner class GetVisitsByFilter {

    private var visitMin: Visit? = null
    private var visitFull: Visit? = null

    @BeforeEach
    internal fun createVisits() {

      visitMin = visitCreator(visitRepository)
        .withPrisonerId("FF0000AA")
        .withVisitStart(visitTime)
        .withPrisonId("MDI")
        .save()

      visitFull = visitCreator(visitRepository)
        .withPrisonerId("FF0000BB")
        .withVisitStart(visitTime.plusDays(1))
        .withVisitEnd(visitTime.plusDays(1).plusHours(1))
        .withPrisonId("LEI")
        .save()

      visitNoteCreator(visit = visitFull!!, text = "A visit concern", type = VISITOR_CONCERN)
      visitNoteCreator(visit = visitFull!!, text = "A visit outcome", type = VISIT_OUTCOMES)
      visitNoteCreator(visit = visitFull!!, text = "A visit comment", type = VISIT_COMMENT)
      visitNoteCreator(visit = visitFull!!, text = "Status has changed", type = STATUS_CHANGED_REASON)

      visitRepository.saveAndFlush(visitFull!!)

      val visitCC = visitCreator(visitRepository)
        .withPrisonerId("FF0000CC")
        .withVisitStart(visitTime.plusDays(2))
        .withVisitEnd(visitTime.plusDays(2).plusHours(1))
        .withPrisonId("LEI")
        .save()
      visitContactCreator(visit = visitCC, name = "Jane Doe", phone = "01234 098765")
      visitVisitorCreator(visit = visitCC, nomisPersonId = 123L)
      visitSupportCreator(visit = visitCC, name = "OTHER", details = "Some Text")

      visitRepository.saveAndFlush(visitCC)

      visitCreator(visitRepository)
        .withPrisonerId("GG0000BB")
        .withVisitStart(visitTime.plusHours(1))
        .withVisitEnd(visitTime.plusHours(2))
        .withPrisonId("BEI")
        .save()

      visitCreator(visitRepository)
        .withPrisonerId("GG0000BB")
        .withVisitStart(visitTime.plusDays(1).plusHours(1))
        .withVisitEnd(visitTime.plusDays(1).plusHours(2))
        .withPrisonId("BEI")
        .save()

      visitCreator(visitRepository)
        .withPrisonerId("GG0000BB")
        .withVisitStart(visitTime.plusDays(2).plusHours(1))
        .withVisitEnd(visitTime.plusDays(2).plusHours(2))
        .withPrisonId("BEI")
        .save()
    }

    @Test
    fun `get visit by prisoner ID`() {
      webTestClient.get().uri("/visits?prisonerId=FF0000BB")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].prisonerId").isEqualTo("FF0000BB")
        .jsonPath("$[0].startTimestamp").isEqualTo(visitFull?.visitStart.toString())
        .jsonPath("$[0].reference").exists()
        .jsonPath("$[0].visitNotes[0].type").isEqualTo("VISITOR_CONCERN")
        .jsonPath("$[0].visitNotes[1].type").isEqualTo("VISIT_OUTCOMES")
        .jsonPath("$[0].visitNotes[2].type").isEqualTo("VISIT_COMMENT")
        .jsonPath("$[0].visitNotes[3].type").isEqualTo("STATUS_CHANGED_REASON")
        .jsonPath("$[0].visitNotes[0].text").isEqualTo("A visit concern")
        .jsonPath("$[0].visitNotes[1].text").isEqualTo("A visit outcome")
        .jsonPath("$[0].visitNotes[2].text").isEqualTo("A visit comment")
        .jsonPath("$[0].visitNotes[3].text").isEqualTo("Status has changed")
    }

    @Test
    fun `get visit by prison ID`() {
      webTestClient.get().uri("/visits?prisonId=LEI")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$..prisonerId").value(
          Matchers.contains(
            "FF0000BB",
            "FF0000CC"
          )
        )
        .jsonPath("$..prisonId").value(
          Matchers.contains(
            "LEI",
            "LEI"
          )
        )
    }

    @Test
    fun `get visits by prison ID and starting on or after a specified date`() {
      webTestClient.get().uri("/visits?prisonId=LEI&startTimestamp=2021-11-03T09:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].startTimestamp").isEqualTo(visitTime.plusDays(2).toString())
    }

    @Test
    fun `get visits by prisoner ID, prison ID and starting on or after a specified date and time`() {
      webTestClient.get().uri("/visits?prisonerId=GG0000BB&prisonId=BEI&startTimestamp=2021-11-01T13:30:45")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$..startTimestamp").value(
          Matchers.contains(
            "2021-11-02T13:30:44",
            "2021-11-03T13:30:44",
          )
        )
        .jsonPath("$..prisonId").value(
          Matchers.contains(
            "BEI",
            "BEI"
          )
        )
        .jsonPath("$..prisonerId").value(
          Matchers.contains(
            "GG0000BB",
            "GG0000BB"
          )
        )
    }

    @Test
    fun `get visits starting before a specified date`() {
      webTestClient.get().uri("/visits?endTimestamp=2021-11-03T09:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(4)
        .jsonPath("$..startTimestamp").value(
          Matchers.contains(
            "2021-11-01T12:30:44",
            "2021-11-01T13:30:44",
            "2021-11-02T12:30:44",
            "2021-11-02T13:30:44"
          )
        )
    }

    @Test
    fun `get visits by visitor`() {
      webTestClient.get().uri("/visits?nomisPersonId=123")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].prisonerId").isEqualTo("FF0000CC")
    }

    @Test
    fun `get visits starting within a date range`() {
      webTestClient.get().uri("/visits?startTimestamp=2021-11-02T09:00:00&endTimestamp=2021-11-03T09:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$..startTimestamp").value(
          Matchers.contains(
            "2021-11-02T12:30:44",
            "2021-11-02T13:30:44"
          )
        )
    }

    @Test
    fun `get visit by reference`() {
      val createdVisit = visitCreator(visitRepository)
        .withPrisonerId("FF0000AA")
        .withVisitStart(visitTime)
        .save()

      webTestClient.get().uri("/visits/${createdVisit.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.reference").isEqualTo(createdVisit.reference)
    }

    @Test
    fun `get visit by reference - not found`() {
      webTestClient.get().uri("/visits/12345")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `no visits found for prisoner`() {
      webTestClient.get().uri("/visits?prisonerId=12345")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `get visits - invalid request, contact id should be a long`() {
      webTestClient.get().uri("/visits?nomisPersonId=123LL")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/visits?prisonerId=FF0000AA")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `unauthorised when no token`() {
      webTestClient.get().uri("/visits?prisonerId=FF0000AA")
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  @DisplayName("Update PUT /visits")
  @Nested
  inner class UpdateVisitBuyPut {

    private var visitMin: Visit? = null
    private var visitFull: Visit? = null

    @BeforeEach
    internal fun createVisits() {
      visitMin = visitCreator(visitRepository)
        .withPrisonerId("FF0000AA")
        .withPrisonId("AAA")
        .withVisitRoom("A1")
        .withVisitStart(visitTime)
        .withVisitEnd(visitTime.plusHours(1))
        .withVisitType(VisitType.SOCIAL)
        .withVisitStatus(VisitStatus.RESERVED)
        .save()

      visitFull = visitCreator(visitRepository)
        .withPrisonerId("FF0000BB")
        .withPrisonId("BBB")
        .withVisitRoom("B1")
        .withVisitStart(visitTime.plusDays(2))
        .withVisitEnd(visitTime.plusDays(2).plusHours(1))
        .withVisitType(VisitType.FAMILY)
        .withVisitStatus(VisitStatus.BOOKED)
        .save()
      visitNoteCreator(visit = visitFull!!, text = "Some text outcomes", type = VISIT_OUTCOMES)
      visitNoteCreator(visit = visitFull!!, text = "Some text concerns", type = VISITOR_CONCERN)
      visitNoteCreator(visit = visitFull!!, text = "Some text comment", type = VISIT_COMMENT)
      visitContactCreator(visit = visitFull!!, name = "Jane Doe", phone = "01234 098765")
      visitVisitorCreator(visit = visitFull!!, nomisPersonId = 321L)
      visitSupportCreator(visit = visitFull!!, name = "OTHER", details = "Some Text")
      visitRepository.saveAndFlush(visitFull!!)
    }

    @Test
    fun `update visit by reference - add booked details`() {

      val updateRequest = UpdateVisitRequestDto(
        prisonerId = "FF0000AB",
        prisonId = "AAB",
        visitRoom = "A2",
        startTimestamp = visitTime.plusDays(2),
        endTimestamp = visitTime.plusDays(2).plusHours(1),
        visitType = VisitType.FAMILY,
        visitStatus = VisitStatus.BOOKED,
        visitRestriction = VisitRestriction.CLOSED,
        visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
        visitors = listOf(CreateVisitorOnVisitRequestDto(123L)),
        visitorSupport = listOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
      )

      webTestClient.put().uri("/visits/${visitMin!!.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(updateRequest)
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.prisonerId").isEqualTo(updateRequest.prisonerId!!)
        .jsonPath("$.prisonId").isEqualTo(updateRequest.prisonId!!)
        .jsonPath("$.visitRoom").isEqualTo(updateRequest.visitRoom!!)
        .jsonPath("$.startTimestamp").isEqualTo(updateRequest.startTimestamp.toString())
        .jsonPath("$.endTimestamp").isEqualTo(updateRequest.endTimestamp.toString())
        .jsonPath("$.visitType").isEqualTo(updateRequest.visitType!!.name)
        .jsonPath("$.visitStatus").isEqualTo(updateRequest.visitStatus!!.name)
        .jsonPath("$.visitRestriction").isEqualTo(updateRequest.visitRestriction!!.name)
        .jsonPath("$.visitContact.name").isNotEmpty
        .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
        .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
        .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
        .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!![0].nomisPersonId)
        .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
        .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!![0].type)
        .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!![0].text!!)
    }

    @Test
    fun `put visit by reference - amend booked details`() {

      val updateRequest = UpdateVisitRequestDto(
        prisonerId = "FF0000AB",
        prisonId = "AAB",
        visitRoom = "A2",
        startTimestamp = visitTime.plusDays(2),
        endTimestamp = visitTime.plusDays(2).plusHours(1),
        visitType = VisitType.FAMILY,
        visitStatus = VisitStatus.BOOKED,
        visitRestriction = VisitRestriction.CLOSED,
        visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
        visitors = listOf(CreateVisitorOnVisitRequestDto(123L)),
        visitorSupport = listOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
      )

      webTestClient.put().uri("/visits/${visitFull!!.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(updateRequest)
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.prisonerId").isEqualTo(updateRequest.prisonerId!!)
        .jsonPath("$.prisonId").isEqualTo(updateRequest.prisonId!!)
        .jsonPath("$.visitRoom").isEqualTo(updateRequest.visitRoom!!)
        .jsonPath("$.startTimestamp").isEqualTo(updateRequest.startTimestamp.toString())
        .jsonPath("$.endTimestamp").isEqualTo(updateRequest.endTimestamp.toString())
        .jsonPath("$.visitType").isEqualTo(updateRequest.visitType!!.name)
        .jsonPath("$.visitStatus").isEqualTo(updateRequest.visitStatus!!.name)
        .jsonPath("$.visitRestriction").isEqualTo(updateRequest.visitRestriction!!.name)
        .jsonPath("$.visitContact.name").isNotEmpty
        .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
        .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
        .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
        .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!![0].nomisPersonId)
        .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
        .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!![0].type)
        .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!![0].text!!)
    }

    @Test
    fun `put visit by reference - amend contact`() {

      val updateRequest = UpdateVisitRequestDto(
        visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
      )

      webTestClient.put().uri("/visits/${visitFull!!.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(updateRequest)
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.visitContact.name").isNotEmpty
        .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
        .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
    }

    @Test
    fun `put visit by reference - amend visitors`() {

      val updateRequest = UpdateVisitRequestDto(
        visitors = listOf(CreateVisitorOnVisitRequestDto(123L)),
      )

      webTestClient.put().uri("/visits/${visitFull!!.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(updateRequest)
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
        .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!![0].nomisPersonId)
    }

    @Test
    fun `put visit by reference - amend support`() {

      val updateRequest = UpdateVisitRequestDto(
        visitorSupport = listOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
      )

      webTestClient.put().uri("/visits/${visitFull!!.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(updateRequest)
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
        .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!![0].type)
        .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!![0].text!!)
    }

    @Test
    fun `put visit by reference - not found`() {
      webTestClient.put().uri("/visits/12345")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            UpdateVisitRequestDto()
          )
        )
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/visits/12345")
        .headers(setAuthorisation(roles = listOf()))
        .body(
          BodyInserters.fromValue(
            UpdateVisitRequestDto()
          )
        )
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `unauthorised when no token`() {
      webTestClient.put().uri("/visits/12345")
        .body(
          BodyInserters.fromValue(
            UpdateVisitRequestDto()
          )
        )
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  @DisplayName("DELETE /visits/{reference}")
  @Nested
  inner class DeleteVisitByReference {
    @Test
    fun `delete visit by reference`() {
      val visitCC = visitCreator(visitRepository)
        .withPrisonerId("FF0000CC")
        .withVisitStart(visitTime.plusDays(2))
        .withVisitEnd(visitTime.plusDays(2).plusHours(1))
        .withPrisonId("LEI")
        .save()
      visitContactCreator(visit = visitCC, name = "Jane Doe", phone = "01234 098765")
      visitVisitorCreator(visit = visitCC, nomisPersonId = 123L)
      visitSupportCreator(visit = visitCC, name = "OTHER", details = "Some Text")
      visitRepository.saveAndFlush(visitCC)

      webTestClient.delete().uri("/visits/${visitCC.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk

      webTestClient.get().uri("/visits/${visitCC.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `delete visit by reference NOT Found`() {
      webTestClient.delete().uri("/visits/123456")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
    }
  }

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }
}
