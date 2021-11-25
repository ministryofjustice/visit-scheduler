package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitorPk

@Repository
interface VisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {
  fun findByPrisonerId(prisonerId: String): List<Visit>
}

@Repository
interface VisitVisitorRepository : JpaRepository<VisitVisitor, VisitVisitorPk>, JpaSpecificationExecutor<Visit>
