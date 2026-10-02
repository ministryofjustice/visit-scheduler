package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonClientType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplateUserClient

@Repository
interface SessionTemplateUserClientRepository : JpaRepository<SessionTemplateUserClient, Long> {
  @Query(
    "SELECT COUNT(stuc) > 0 FROM SessionTemplateUserClient stuc " +
      "WHERE stuc.clientType = :clientType AND stuc.sessionTemplate.reference = :sessionTemplateReference",
  )
  fun doesSessionTemplateClientExist(sessionTemplateReference: String, clientType: PrisonClientType): Boolean

  @Query(
    "SELECT stuc FROM SessionTemplateUserClient stuc " +
      "WHERE stuc.clientType = :clientType AND stuc.sessionTemplate.reference = :sessionTemplateReference",
  )
  fun getSessionTemplateClient(sessionTemplateReference: String, clientType: PrisonClientType): SessionTemplateUserClient
}
