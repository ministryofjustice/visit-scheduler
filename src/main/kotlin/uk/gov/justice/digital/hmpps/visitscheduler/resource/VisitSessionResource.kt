package uk.gov.justice.digital.hmpps.visitscheduler.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitSession
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitSchedulerService

@RestController
@Validated
@RequestMapping(name = "Visit Resource", path = ["/visit-sessions"], produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitSessionResource(
  private val visitSchedulerService: VisitSchedulerService
) {

  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @GetMapping("/prison/{prisonId}")
  @Operation(
    summary = "Returns all visit sessions which are within the reservable time period - whether or not they are full",
    description = "Retrieve all visits for a specified prisoner",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit session information returned"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visit sessions ",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getVisitSessions(
    @Schema(description = "NOMIS Prison Identifier", example = "AD12345G", required = true)
    @PathVariable prisonId: String
  ): List<VisitSession> =
    visitSchedulerService.getVisitSessions(prisonId)
}
