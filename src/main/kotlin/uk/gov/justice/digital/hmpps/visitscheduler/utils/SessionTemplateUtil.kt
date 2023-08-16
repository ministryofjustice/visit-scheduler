package uk.gov.justice.digital.hmpps.visitscheduler.utils

import jakarta.persistence.Tuple
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import java.util.*
import java.util.stream.Collectors

@Component
class SessionTemplateUtil {
  val sessionCapacityComparator: Comparator<SessionCapacityDto> =
    Comparator { o1: SessionCapacityDto, o2: SessionCapacityDto ->
      if (o1 == o2) {
        0
      } else if (o1.open < o2.open || o1.closed < o2.closed) {
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

  fun getTotalCapacity(a: SessionCapacityDto, b: SessionCapacityDto): SessionCapacityDto {
    return SessionCapacityDto(
      open = a.open + b.open,
      closed = a.closed + b.closed,
    )
  }

  fun getPermittedSessionLocations(permittedLocationGroups: List<SessionLocationGroupDto>): Set<PermittedSessionLocationDto> {
    return permittedLocationGroups.stream()
      .map { it.locations }
      .flatMap(List<PermittedSessionLocationDto>::stream).collect(Collectors.toSet())
  }

  fun getPermittedPrisonerCategoryTypes(prisonerCategoryGroups: List<SessionCategoryGroupDto>): Set<PrisonerCategoryType> {
    return prisonerCategoryGroups.stream()
      .map { it.categories }
      .flatMap(List<PrisonerCategoryType>::stream).collect(Collectors.toSet())
  }

  fun getPermittedIncentiveLevels(prisonerIncentiveLevelGroups: List<SessionIncentiveLevelGroupDto>): Set<IncentiveLevel> {
    return prisonerIncentiveLevelGroups.stream()
      .map { it.incentiveLevels }
      .flatMap(List<IncentiveLevel>::stream).collect(Collectors.toSet())
  }
}
