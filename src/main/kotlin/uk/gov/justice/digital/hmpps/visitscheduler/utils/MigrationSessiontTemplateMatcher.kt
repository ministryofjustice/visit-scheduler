package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.exception.MatchSessionTemplateToMigratedVisitException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.absoluteValue

const val DEFAULT_MAX_PROX_MINUTES = 180
private const val FROM_DATE_IN_FUTURE = -1000

@Component
class MigrationSessionTemplateMatcher(
  private val prisonerService: PrisonerService,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val prisonerCategoryMatcher: PrisonerCategoryMatcher,
  private val prisonerIncentiveLevelMatcher: PrisonerIncentiveLevelMatcher,
  private val sessionValidator: PrisonerSessionValidator,
  private val sessionDatesUtil: SessionDatesUtil,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${policy.session.max-proximity-minutes:$DEFAULT_MAX_PROX_MINUTES}")
    var maxProximityMinutes: Int = DEFAULT_MAX_PROX_MINUTES
  }

  private class MigrateMatch() : Comparable<MigrateMatch> {
    // locationScore should always be -1 as a default as it only scores on valid locations
    var locationScore: Int = -1
    var category: Boolean = false
    var enhanced: Boolean = false
    var timeProximity: Int = 0
    var validFromDateProximityDays: Int = 0
    var roomNameMatch: Boolean = false

    override fun compareTo(other: MigrateMatch): Int {
      // smaller proximity the better, hence -timeProximity
      val timeProximityCompare = -timeProximity.compareTo(other.timeProximity)

      var compareValue =
        timeProximityCompare +
          locationScore.compareTo(other.locationScore) +
          category.compareTo(other.category) +
          enhanced.compareTo(other.enhanced)

      if (compareValue == 0) {
        compareValue = validFromDateProximityDays.compareTo(other.validFromDateProximityDays)
        if (compareValue == 0) {
          compareValue = roomNameMatch.compareTo(other.roomNameMatch)
        }
      }

      return compareValue
    }
  }

  private fun getSessionsTemplates(
    prisonCode: String,
    sessionDate: LocalDate,
    restriction: VisitRestriction,
  ): List<SessionTemplate> {
    var templates = sessionTemplateRepository.findValidSessionTemplatesBy(
      rangeStartDate = sessionDate,
      prisonCode = prisonCode,
      dayOfWeek = sessionDate.dayOfWeek,
    )

    templates = templates.filter {
      sessionDatesUtil.isActiveForDate(sessionDate, it)
    }

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
    val startDate = migrateVisitRequest.startTimestamp.toLocalDate()
    val startTime = migrateVisitRequest.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequest.endTimestamp.toLocalTime()
    val prisonCode = migrateVisitRequest.prisonCode
    val prisonerId = migrateVisitRequest.prisonerId

    val message = "prison code $prisonCode prisoner id $prisonerId,  bookedDate : ${migrateVisitRequest.createDateTime}, visit $startDate/${startDate.dayOfWeek}/$startTime <> $endTime room:${migrateVisitRequest.visitRoom}"
    LOG.debug("Enter getNearestSessionTemplate : $message")

    if (sessionTemplates.isEmpty()) {
      throw MatchSessionTemplateToMigratedVisitException("getNearestSessionTemplate : Could not find any SessionTemplates : $message, No session templates!")
    }

    val matchedSessionTemplate = sessionTemplates.associateWith { MigrateMatch() }
    findLocationMatch(prisonerId, prisonCode, sessionTemplates, matchedSessionTemplate)

    val prisonerDto = prisonerService.getPrisoner(prisonerId)
    if (prisonerDto == null) {
      throw MatchSessionTemplateToMigratedVisitException("getNearestSessionTemplate : Prisoner cannot be found : $message!")
    } else if (prisonCode != prisonerDto.prisonCode) {
      LOG.debug("getNearestSessionTemplate : migrated $prisonerId prison ($prisonCode) has a different location prison ($prisonerDto)!")
    }

    val prisonerDetailDto = prisonerService.getPrisonerHousingLocation(prisonerId, prisonCode)!!
    val prisonLevelMap = prisonerService.getLevelsMapForPrisoner(prisonerDetailDto)

    sessionTemplates.forEach { template ->
      val matcher = matchedSessionTemplate[template]
      matcher?.let {
        with(it) {
          locationScore = sessionValidator.getLocationScore(prisonLevelMap, template)
          category = prisonerCategoryMatcher.test(prisonerDto.category, template)
          enhanced = prisonerIncentiveLevelMatcher.test(prisonerDto.incentiveLevel, template)
          timeProximity = getProximityMinutes(template.startTime, startTime, template.endTime, endTime)
          validFromDateProximityDays = getFromDateProximityDays(migrateVisitRequest, template)
          roomNameMatch = template.visitRoom == migrateVisitRequest.visitRoom
        }
      }
    }

    val templateMatchComparator = Comparator<SessionTemplate> { template1, template2 ->
      val matcher1 = matchedSessionTemplate[template1]!!
      val matcher2 = matchedSessionTemplate[template2]!!
      matcher1.compareTo(matcher2)
    }

    val orderedSessionTemplates = sessionTemplates.sortedWith(templateMatchComparator).filter {
      val migrateMatch = matchedSessionTemplate[it]!!
      val keep = isSessionPermitted(it, migrateMatch)
      if (!keep) {
        logMatchDetails(migrateMatch, it, prisonCode, prisonerId, prisonLevelMap)
      }
      keep
    }

    val bestMatch = orderedSessionTemplates.lastOrNull()
      ?: throw MatchSessionTemplateToMigratedVisitException("getNearestSessionTemplate : Could not find any matching SessionTemplates matching prisoner : $prisonerDto, location : ${locationToString(prisonLevelMap)} ,$message!")

    with(matchedSessionTemplate[bestMatch]!!) {
      LOG.debug("getNearestSessionTemplate, ref:${bestMatch.reference}/$prisonCode/$prisonerId locationScore:$locationScore category:$category enhanced:$enhanced timeProximity:$timeProximity roomMatch:$roomNameMatch dateProximity:$validFromDateProximityDays")
    }

    return bestMatch
  }

  private fun logMatchDetails(
    migrateMatch: MigrateMatch,
    it: SessionTemplate,
    prisonCode: String,
    prisonerId: String,
    prisonLevelMap: Map<PrisonerHousingLevels, String?>,
  ) {
    with(migrateMatch) {
      val builder = StringBuilder()
      builder.append("getNearestSessionTemplate session is not permitted ")
      builder.append("ref:${it.reference}/$prisonCode/$prisonerId ")
      builder.append("locationScore:$locationScore ")
      builder.append("category:$category ")
      builder.append("enhanced:$enhanced ")
      builder.append("timeProximity:$timeProximity ")
      builder.append("roomMatch:$roomNameMatch ")
      builder.append("dateProximity:$validFromDateProximityDays ")
      builder.append("location:${locationToString(prisonLevelMap)} ")
      LOG.debug(builder.toString())
    }
  }

  private fun locationToString(prisonLevelMap: Map<PrisonerHousingLevels, String?>): String {
    return prisonLevelMap.values.filterNotNull().toString()
  }

  private fun isSessionPermitted(
    template: SessionTemplate,
    migrateMatch: MigrateMatch,
  ): Boolean {
    with(migrateMatch) {
      val isAllowed = locationScore != LOCATION_NOT_PERMITTED &&
        (category || sessionValidator.isSessionForAllCategories(template)) &&
        (enhanced || sessionValidator.isSessionForAllIncentiveLevels(template)) &&
        timeProximity <= maxProximityMinutes &&
        migrateMatch.validFromDateProximityDays != FROM_DATE_IN_FUTURE
      return isAllowed
    }
  }

  private fun findLocationMatch(
    prisonerId: String,
    prisonCode: String,
    sessionTemplates: List<SessionTemplate>,
    matchedSessionTemplate: Map<SessionTemplate, MigrateMatch>,
  ) {
    val prisonerDetailDto = prisonerService.getPrisonerHousingLocation(prisonerId, prisonCode)!!
    val prisonLevelMap = prisonerService.getLevelsMapForPrisoner(prisonerDetailDto)
    sessionTemplates.forEach { template ->
      matchedSessionTemplate[template]?.locationScore = sessionValidator.getLocationScore(prisonLevelMap, template)
    }
  }

  private fun getFromDateProximityDays(
    migrateVisitRequest: MigrateVisitRequestDto,
    template: SessionTemplate,
  ): Int {
    val migratedVisitDate = migrateVisitRequest.startTimestamp.toLocalDate()
    if (template.validFromDate.isAfter(migratedVisitDate)) {
      // Template from date is after the migrated visit date therefore not valid
      return FROM_DATE_IN_FUTURE
    }
    return DAYS.between(migratedVisitDate, template.validFromDate).toInt()
  }
}
