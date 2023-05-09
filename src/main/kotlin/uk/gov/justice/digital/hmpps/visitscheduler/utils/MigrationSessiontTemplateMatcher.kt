package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.MatchSessionTemplateToMigratedVisitException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.absoluteValue

const val DEFAULT_MAX_PROX_MINUTES = 180

@Component
class MigrationSessionTemplateMatcher(
  private val prisonerService: PrisonerService,
  private val sessionService: SessionService,
  private val sessionTemplateRepository: SessionTemplateRepository,
) {

  companion object {
    @Value("\${policy.session.max-proximity-minutes:$DEFAULT_MAX_PROX_MINUTES}")
    var maxProximityMinutes: Int = DEFAULT_MAX_PROX_MINUTES
  }

  private class MigrateMatch() : Comparable<MigrateMatch> {
    // locationScore should always be -1 as a default as it only scores on valid locations
    var locationScore: Int = -1
    var category: Boolean = false
    var enhanced: Boolean = false
    var timeProximity: Int = 0
    var validDateProximity: Int = 0
    var roomNameMatch: Boolean = false

    override fun compareTo(other: MigrateMatch): Int {
      if (!isValidProximity()) return -1

      // smaller proximity the better, hence -timeProximity
      val timeProximityCompare = compareScour(-timeProximity.compareTo(other.timeProximity))

      var compareValue =
        timeProximityCompare +
          compareScour(locationScore.compareTo(other.locationScore)) +
          category.compareTo(other.category) +
          enhanced.compareTo(other.enhanced) +
          compareScour(validDateProximity.compareTo(other.validDateProximity))

      if (compareValue == 0) {
        compareValue = validDateProximity.compareTo(other.validDateProximity)
        if (compareValue == 0) {
          compareValue = roomNameMatch.compareTo(other.roomNameMatch)
        }
      }
      return compareValue
    }

    fun isValidProximity(): Boolean {
      return (timeProximity <= maxProximityMinutes)
    }

    private fun compareScour(value: Int): Int {
      if (value > 0) return 1
      if (value < 0) return -1
      return 0
    }
  }

  private fun getSessionsTemplates(
    prisonCode: String,
    sessionDate: LocalDate,
    restriction: VisitRestriction,
  ): List<SessionTemplate> {
    val templates = sessionTemplateRepository.findValidSessionTemplatesBy(
      rangeStartDate = sessionDate,
      prisonCode = prisonCode,
      dayOfWeek = sessionDate.dayOfWeek,
    )

    return removeUnwantedRestrictionTypes(restriction, templates)
  }

  private fun removeUnwantedRestrictionTypes(restriction: VisitRestriction, sessionTemplates: List<SessionTemplate>): List<SessionTemplate> {
    if (restriction != UNKNOWN) {
      // Must have a valid capacity for future visits
      return sessionTemplates.filter {
        if (restriction == OPEN) it.openCapacity > 0 else it.closedCapacity > 0
      }
    }
    return sessionTemplates
  }

  private fun getProximityMinutes(sessionStartTime: LocalTime, startTime: LocalTime, sessionEndTime: LocalTime, endTime: LocalTime): Int {
    return (
      (
        (sessionStartTime.toSecondOfDay() - startTime.toSecondOfDay()).absoluteValue +
          (sessionEndTime.toSecondOfDay() - endTime.toSecondOfDay()).absoluteValue
        ) / 60
      )
  }

  fun getMatchingSessionTemplate(
    migrateVisitRequest: MigrateVisitRequestDto,
  ): SessionTemplate {
    with(migrateVisitRequest) {
      val sessionTemplates = getSessionsTemplates(
        prisonCode,
        startTimestamp.toLocalDate(),
        migrateVisitRequest.visitRestriction,
      )

      return getNearestSessionTemplate(migrateVisitRequest, sessionTemplates)
    }
  }

  fun getNearestSessionTemplate(
    migrateVisitRequest: MigrateVisitRequestDto,
    sessionTemplates: List<SessionTemplate>,
  ): SessionTemplate {
    val matchedSessionTemplate = sessionTemplates.associateWith { MigrateMatch() }

    val sessionLocationTemplates = sessionService.filterSessionsTemplatesForLocation(
      sessionTemplates,
      migrateVisitRequest.prisonerId,
      migrateVisitRequest.prisonCode,
      true,
    )
    if (sessionLocationTemplates.isNotEmpty()) {
      sessionLocationTemplates.forEach { template ->
        matchedSessionTemplate[template]?.locationScore = getLocationMatchScore(template)
      }
    }

    val prisonerDto = prisonerService.getPrisoner(migrateVisitRequest.prisonerId)!!
    val startDate = migrateVisitRequest.startTimestamp.toLocalDate()
    val startTime = migrateVisitRequest.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequest.endTimestamp.toLocalTime()

    sessionTemplates.forEach { template ->
      val matcher = matchedSessionTemplate[template]
      matcher?.let {
        if (prisonerDto.category != null) {
          it.category = sessionService.isPrisonerCategoryAllowedOnSession(template, prisonerDto.category)
        }
        it.enhanced = template.enhanced == true && prisonerDto.enhanced == true
        it.timeProximity = getProximityMinutes(template.startTime, startTime, template.endTime, endTime)
        it.roomNameMatch = template.visitRoom == migrateVisitRequest.visitRoom
        it.validDateProximity = getDateProximityDays(migrateVisitRequest, template)
      }
    }

    val templateMatchComparator = Comparator<SessionTemplate> { template1, template2 ->
      val matcher1 = matchedSessionTemplate[template1]!!
      val matcher2 = matchedSessionTemplate[template2]!!
      matcher1.compareTo(matcher2)
    }

    val orderedSessionTemplates = sessionTemplates.sortedWith(templateMatchComparator)
    val bestMatch = orderedSessionTemplates.lastOrNull()
      ?: throw MatchSessionTemplateToMigratedVisitException("Could not find any SessionTemplate for future visit date $startDate/${startDate.dayOfWeek}/$startTime")

    if (!matchedSessionTemplate[bestMatch]!!.isValidProximity()) {
      throw MatchSessionTemplateToMigratedVisitException("Not valid proximity, Could not find any SessionTemplate for future visit date $startDate/${startDate.dayOfWeek}/$startTime")
    }
    return bestMatch
  }

  private fun getDateProximityDays(
    migrateVisitRequest: MigrateVisitRequestDto,
    template: SessionTemplate,
  ): Int {
    val dateProximity = DAYS.between(migrateVisitRequest.startTimestamp.toLocalDate(), template.validFromDate)
    return if (dateProximity > 0) -100 else dateProximity.toInt()
  }

  private fun getLocationMatchScore(locationMatchingSessionTemplate: SessionTemplate): Int {
    var highestScore = 0

    locationMatchingSessionTemplate.permittedSessionLocationGroups.forEach { locationGroup ->
      locationGroup.sessionLocations.forEach {
        with(it) {
          val score = levelFourCode?.let { 4 } ?: levelThreeCode?.let { 3 } ?: levelTwoCode?.let { 2 } ?: levelOneCode.let { 1 } ?: 0
          if (score > highestScore) {
            highestScore = score
          }
        }
      }
    }
    return highestScore
  }
}
