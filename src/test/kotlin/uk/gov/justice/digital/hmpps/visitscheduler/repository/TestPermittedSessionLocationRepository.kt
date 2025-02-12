package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation

@Repository
interface TestPermittedSessionLocationRepository :
  JpaRepository<PermittedSessionLocation, Long>,
  JpaSpecificationExecutor<PermittedSessionLocation>
