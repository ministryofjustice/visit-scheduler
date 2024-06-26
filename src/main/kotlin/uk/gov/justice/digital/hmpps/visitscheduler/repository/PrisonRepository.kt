package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison

@Repository
interface PrisonRepository : JpaRepository<Prison, Long> {

  fun findByCode(prisonCode: String): Prison?

  @Query(
    "SELECT p.code FROM Prison p " +
      " JOIN PrisonUserClient puc ON puc.prisonId = p.id " +
      " WHERE p.active = true AND puc.userType = :type AND puc.active = true " +
      " ORDER BY p.code",
  )
  fun getSupportedPrisons(type: UserType): List<String>

  @Query(
    "SELECT p.code FROM Prison p " +
      " ORDER BY p.code",
  )
  fun getPrisonCodes(): List<String>

  @Query(
    "select p.code from Prison p " +
      "where p.code=:prisonCode",
  )
  fun getPrisonCode(prisonCode: String): String?

  fun findAllByOrderByCodeAsc(): List<Prison>

  @Query(
    "select p.id from Prison p " +
      "where p.code=:prisonCode",
  )
  fun getPrisonId(prisonCode: String): Long?
}
