package uk.gov.justice.digital.hmpps.visitscheduler.utils

import jakarta.persistence.Tuple
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import java.util.*
import java.util.stream.Collectors

@Component
class SessionTemplateUtil {
  val sessionCapacityComparator: Comparator<SessionCapacityDto> =
    Comparator { sessionCapacityOne: SessionCapacityDto, sessionCapacityTwo: SessionCapacityDto ->
      if (sessionCapacityOne == sessionCapacityTwo) {
        0
      } else if (sessionCapacityOne.open < sessionCapacityTwo.open || sessionCapacityOne.closed < sessionCapacityTwo.closed) {
        -1
      } else {
        1
      }
    }

  fun getMinimumSessionCapacity(minimumCapacityTuple: Tuple): SessionCapacityDto {
    val emptyResults = minimumCapacityTuple.get(0) == null
    val open = if (emptyResults) 0 else (minimumCapacityTuple.get(0) as Long).toInt()
    val closed = if (emptyResults) 0 else (minimumCapacityTuple.get(1) as Long).toInt()

    return SessionCapacityDto(closed = closed, open = open)
  }

  fun getPermittedSessionLocations(permittedLocationGroups: List<SessionLocationGroupDto>): Set<PermittedSessionLocationDto> = permittedLocationGroups.stream()
    .map { it.locations }
    .flatMap(List<PermittedSessionLocationDto>::stream).collect(Collectors.toSet())

  fun getPermittedPrisonerCategoryTypes(prisonerCategoryGroups: List<SessionCategoryGroupDto>): Set<PrisonerCategoryType> = prisonerCategoryGroups.stream()
    .map { it.categories }
    .flatMap(List<PrisonerCategoryType>::stream).collect(Collectors.toSet())

  fun getPermittedIncentiveLevels(prisonerIncentiveLevelGroups: List<SessionIncentiveLevelGroupDto>): Set<IncentiveLevel> = prisonerIncentiveLevelGroups.stream()
    .map { it.incentiveLevels }
    .flatMap(List<IncentiveLevel>::stream).collect(Collectors.toSet())

  fun isCapacityExceeded(allowedSessionCapacity: SessionCapacityDto, totalSessionCapacity: SessionCapacityDto): Boolean = sessionCapacityComparator.compare(allowedSessionCapacity, totalSessionCapacity) < 0
}
