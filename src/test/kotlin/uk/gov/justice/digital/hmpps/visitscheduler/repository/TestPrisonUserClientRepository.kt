package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonUserClient

@Repository
interface TestPrisonUserClientRepository : JpaRepository<PrisonUserClient, Long> {
  @Query(
    "SELECT pc FROM PrisonUserClient pc " +
      "WHERE pc.userType = :userType AND pc.prison.code = :prisonCode",
  )
  fun getPrisonClient(prisonCode: String, userType: UserType): PrisonUserClient?
}
