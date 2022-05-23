package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.format.annotation.DateTimeFormat.ISO
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import java.time.LocalDate

@RequestMapping(name = "Session Resource", path = ["/visit-sessions"], produces = [MediaType.APPLICATION_JSON_VALUE])
@RestController
@Validated
class VisitSessionController(
  private val sessionService: SessionService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping
  @Operation(
    summary = "Returns all visit sessions which are within the reservable time period - whether or not they are full",
    description = "Retrieve all visits for a specified prisoner",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit session information returned"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visit sessions ",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getVisitSessions(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Query by NOMIS Prison Identifier",
      example = "MDI"
    ) prisonId: String,
    @RequestParam(value = "prisonerId", required = false)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC"
    ) prisonerId: String?,
    @RequestParam(value = "min", required = false)
    @Parameter(
      description = "Override the default minimum number of days notice from the current date",
      example = "2"
    ) noticeDaysMin: Long?,
    @RequestParam(value = "max", required = false)
    @Parameter(
      description = "Override the default maximum number of days to book-ahead from the current date",
      example = "28"
    ) noticeDaysMax: Long?
  ): List<VisitSessionDto> {
    return sessionService.getVisitSessions(prisonId, prisonerId, noticeDaysMin, noticeDaysMax)
  }

  @RequestMapping(params = ["firstBookableDate", "lastBookableDate"])
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping
  @Operation(
    summary = "Returns all visit sessions which are within the reservable time period - whether or not they are full",
    description = "Retrieve all visits for a specified prisoner",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit session information returned"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visit sessions ",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getVisitSessions(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Query by NOMIS Prison Identifier",
      example = "MDI"
    ) prisonId: String,
    @RequestParam(value = "prisonerId", required = false)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC"
    ) prisonerId: String?,
    @RequestParam(value = "firstBookableDate", required = true)
    @DateTimeFormat(iso = ISO.DATE)
    @Parameter(
      description = "first bookable date",
      example = "2021-01-08"
    ) firstBookableDate: LocalDate,
    @RequestParam(value = "lastBookableDate", required = true)
    @DateTimeFormat(iso = ISO.DATE)
    @Parameter(
      description = "last bookable date",
      example = "2021-01-10"
    ) lastBookableDate: LocalDate
  ): List<VisitSessionDto> {
    return sessionService.getVisitSessions(prisonId, prisonerId, firstBookableDate, lastBookableDate)
  }
}
