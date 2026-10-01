package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonClientType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonUserClient

@Repository
interface PrisonUserClientRepository : JpaRepository<PrisonUserClient, Long> {

  @Query(
    "SELECT COUNT(pc) > 0 FROM PrisonUserClient pc " +
      "WHERE pc.clientType = :clientType AND pc.prison.code = :prisonCode",
  )
  fun doesPrisonClientExist(prisonCode: String, clientType: PrisonClientType): Boolean

  @Query(
    "SELECT pc FROM PrisonUserClient pc " +
      "WHERE pc.clientType = :clientType AND pc.prison.code = :prisonCode",
  )
  fun getPrisonClient(prisonCode: String, clientType: PrisonClientType): PrisonUserClient
}
