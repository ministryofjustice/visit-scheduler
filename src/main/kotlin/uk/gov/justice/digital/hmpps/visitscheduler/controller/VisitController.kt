package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDate

const val VISIT_CONTROLLER_PATH: String = "/visits"
const val GET_VISIT_HISTORY_CONTROLLER_PATH: String = "$VISIT_CONTROLLER_PATH/{reference}/history"
const val VISIT_CONTROLLER_SEARCH_PATH: String = "$VISIT_CONTROLLER_PATH/search"
const val VISIT_CONTROLLER_SEARCH_FUTURE_VISITS_PATH: String = "$VISIT_CONTROLLER_PATH/search/future/{prisonerNumber}"
const val VISIT_BOOK: String = "$VISIT_CONTROLLER_PATH/{applicationReference}/book"
const val VISIT_CANCEL: String = "$VISIT_CONTROLLER_PATH/{reference}/cancel"
const val GET_VISIT_BY_REFERENCE: String = "$VISIT_CONTROLLER_PATH/{reference}"
const val GET_VISITS_BY: String = "$VISIT_CONTROLLER_PATH/session-template"
const val VISIT_IGNORE_NOTIFICATIONS: String = "$VISIT_CONTROLLER_PATH/{reference}/ignore-notifications"

@RestController
@Validated
@Tag(name = "1. Visit rest controller")
@RequestMapping(name = "Visit Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitController(
  private val visitService: VisitService,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_BOOK)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Book a visit (end of flow)",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit updated",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to book a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to book a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun bookVisit(
    @Schema(description = "applicationReference", example = "dfs-wjs-eqr", required = true)
    @PathVariable
    applicationReference: String,
    @RequestBody @Valid
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    return visitService.bookVisit(applicationReference.trim(), bookingRequestDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_CANCEL)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Cancel an existing booked visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = CancelVisitDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit cancelled",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to cancel a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to cancel a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun cancelVisit(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    cancelVisitDto: CancelVisitDto,
  ): VisitDto {
    return visitService.cancelVisit(reference.trim(), cancelVisitDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_VISIT_HISTORY_CONTROLLER_PATH)
  @Operation(
    summary = "Get visit history",
    description = "Retrieve visit history by visit reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit History Information Returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visit history",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions retrieve visit history",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitHistoryByReference(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): List<EventAuditDto> {
    return visitService.getHistoryByReference(reference.trim())
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_VISIT_BY_REFERENCE)
  @Operation(
    summary = "Get a visit",
    description = "Retrieve visit by visit reference (excludes Reserved and CHANGING)",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit Information Returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visits for prisoner",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions retrieve a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitByReference(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): VisitDto {
    return visitService.getVisitByReference(reference.trim())
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(params = ["page", "size"], path = [VISIT_CONTROLLER_SEARCH_PATH])
  @Operation(
    summary = "Get visits",
    description = "Retrieve visits with optional filters, sorted by start timestamp descending",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit Information Returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visits for prisoner",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to retrieve visits",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitsByFilterPageable(
    @RequestParam(value = "prisonerId", required = false)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC",
    )
    prisonerId: String?,
    @RequestParam(value = "prisonId", required = false)
    @Parameter(
      description = "Filter results by prison id/code",
      example = "MDI",
    )
    prisonCode: String?,
    @RequestParam(value = "visitStartDate", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Parameter(
      description = "Filter results by visits that start on or after the given timestamp",
      example = "2021-11-03",
    )
    visitStartDate: LocalDate?,
    @RequestParam(value = "visitEndDate", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Parameter(
      description = "Filter results by visits that end on or before the given timestamp",
      example = "2021-11-03",
    )
    visitEndDate: LocalDate?,
    @RequestParam(value = "visitStatus", required = true)
    @Parameter(
      description = "Filter results by visit status",
      example = "BOOKED",
    )
    visitStatusList: List<VisitStatus>,
    @RequestParam(value = "page", required = true)
    @Parameter(
      description = "Pagination page number, starting at zero",
      example = "0",
    )
    page: Int,
    @RequestParam(value = "size", required = true)
    @Parameter(
      description = "Pagination size per page",
      example = "50",
    )
    size: Int,
  ): Page<VisitDto> {
    return visitService.findVisitsByFilterPageableDescending(
      VisitFilter(
        prisonerId = prisonerId?.trim(),
        prisonCode = prisonCode?.trim(),
        visitStartDate = visitStartDate,
        visitEndDate = visitEndDate,
        visitStatusList = visitStatusList,
      ),
      page,
      size,
    )
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_VISITS_BY)
  @Operation(
    summary = "Get visits for a date or a range of dates with / without a session template reference",
    description = "Get visits for a date or a range of dates with a session template reference or visits without a session template reference when session template reference is not passed",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns visits for a session template or visits where session template reference is null if no session template reference parameter passed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get visits by session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get visits by session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitsBy(
    @Schema(name = "sessionTemplateReference", description = "Session template reference", example = "v9-d7-ed-7u", required = false)
    @RequestParam
    sessionTemplateReference: String?,
    @Schema(name = "fromDate", description = "Get visits from date", example = "2023-05-31", required = true)
    @RequestParam
    @NotNull
    fromDate: LocalDate,
    @Schema(name = "toDate", description = "Get visits to date", example = "2023-05-31", required = true)
    @RequestParam
    @NotNull
    toDate: LocalDate,
    @Schema(name = "visitRestrictions", description = "Visit Restriction - OPEN / CLOSED / UNKNOWN", example = "OPEN", required = false)
    @RequestParam
    visitRestrictions: List<VisitRestriction>?,
    @RequestParam(value = "visitStatus", required = true)
    @Parameter(
      description = "Filter results by visit status",
      example = "BOOKED",
    )
    @NotNull
    visitStatusList: List<VisitStatus>,
    @Schema(name = "prisonCode", description = "Filter results by prison id/code", example = "MDI", required = true)
    @RequestParam
    @NotNull
    prisonCode: String,
    @RequestParam(value = "page", required = true)
    @Parameter(
      description = "Pagination page number, starting at zero",
      example = "0",
    )
    page: Int,
    @RequestParam(value = "size", required = true)
    @Parameter(
      description = "Pagination size per page",
      example = "50",
    )
    size: Int,
  ): Page<VisitDto> {
    return visitService.findVisitsBySessionTemplateFilterPageableDescending(
      sessionTemplateReference = sessionTemplateReference,
      fromDate = fromDate,
      toDate = toDate,
      visitStatusList = visitStatusList,
      visitRestrictions = visitRestrictions,
      prisonCode = prisonCode,
      pageablePage = page,
      pageableSize = size,
    )
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(VISIT_CONTROLLER_SEARCH_FUTURE_VISITS_PATH)
  @Operation(
    summary = "Get future (booked and cancelled) visits for a prisoner",
    description = "Get future visits for given prisoner number",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returned future visits (booked and cancelled) for a prisoner",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get future (booked and cancelled) visits for a prisoner",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get future (booked and cancelled) visits for a prisoner",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getFutureVisitsBySessionPrisoner(
    @PathVariable(value = "prisonerNumber", required = false)
    @NotBlank
    @Length(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9]+$")
    prisonerNumber: String,
  ): List<VisitDto> {
    return visitService.findFutureVisitsBySessionPrisoner(prisonerNumber)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_IGNORE_NOTIFICATIONS)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Do not change an existing booked visit and ignore all notifications",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = IgnoreVisitNotificationsDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit notifications cleared and reason noted.",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to ignore visit notifications.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to ignore visit notifications.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun ignoreVisitNotifications(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    doNotChangeVisit: IgnoreVisitNotificationsDto,
  ): VisitDto {
    return visitService.ignoreVisitNotifications(reference.trim(), doNotChangeVisit)
  }
}
