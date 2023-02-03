package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDateTime
import javax.validation.Valid

const val VISIT_CONTROLLER_PATH: String = "/visits"
const val VISIT_CONTROLLER_SEARCH_PATH: String = "$VISIT_CONTROLLER_PATH/search"
const val VISIT_RESERVE_SLOT: String = "$VISIT_CONTROLLER_PATH/slot/reserve"
const val VISIT_RESERVED_SLOT_CHANGE: String = "$VISIT_CONTROLLER_PATH/{applicationReference}/slot/change"
const val VISIT_CHANGE: String = "$VISIT_CONTROLLER_PATH/{reference}/change"
const val VISIT_BOOK: String = "$VISIT_CONTROLLER_PATH/{applicationReference}/book"
const val VISIT_CANCEL: String = "$VISIT_CONTROLLER_PATH/{reference}/cancel"
const val GET_VISIT_BY_REFERENCE: String = "$VISIT_CONTROLLER_PATH/{reference}"

@RestController
@Validated
@Tag(name = "1. Visit rest controller")
@RequestMapping(name = "Visit Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitController(
  private val visitService: VisitService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_RESERVE_SLOT)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Reserve a slot (date/time slot) for a visit (a starting point)",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = ReserveVisitSlotDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit slot reserved"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to reserve a slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to reserve a slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun reserveVisitSlot(
    @RequestBody @Valid reserveVisitSlotDto: ReserveVisitSlotDto
  ): VisitDto {
    return visitService.reserveVisitSlot(reserveVisitSlotDto = reserveVisitSlotDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_RESERVED_SLOT_CHANGE)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Change a reserved slot and associated details for a visit (before booking)",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = ChangeVisitSlotRequestDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit slot changed"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to changed a visit slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to changed a visit slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit slot not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
    ]
  )
  fun changeReservedVisitSlot(
    @Schema(description = "applicationReference", example = "dfs-wjs-eqr", required = true)
    @PathVariable applicationReference: String,
    @RequestBody @Valid changeVisitSlotRequestDto: ChangeVisitSlotRequestDto
  ): VisitDto {
    return visitService.changeVisitSlot(applicationReference.trim(), changeVisitSlotRequestDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_CHANGE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Change a booked visit, (a starting point)",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = ReserveVisitSlotDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit created"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to change a booked visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to change a booked visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun changeBookedVisit(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String,
    @RequestBody @Valid reserveVisitSlotDto: ReserveVisitSlotDto
  ): VisitDto {
    return visitService.changeBookedVisit(reference.trim(), reserveVisitSlotDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_BOOK)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Book a visit (end of flow)",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit updated"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to book a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to book a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
    ]
  )
  fun bookVisit(
    @Schema(description = "applicationReference", example = "dfs-wjs-eqr", required = true)
    @PathVariable applicationReference: String
  ): VisitDto {
    return visitService.bookVisit(applicationReference.trim())
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
          schema = Schema(implementation = OutcomeDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit cancelled"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to cancel a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to cancel a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
    ]
  )
  fun cancelVisit(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String,
    @RequestBody @Valid cancelOutcome: OutcomeDto
  ): VisitDto {
    return visitService.cancelVisit(reference.trim(), cancelOutcome)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(params = ["page", "size"], path = [VISIT_CONTROLLER_SEARCH_PATH])
  @Operation(
    summary = "Get visits",
    description = "Retrieve visits with optional filters, sorted by start timestamp descending",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit Information Returned"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visits for prisoner",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to retrieve visits",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getVisitsByFilterPageable(
    @RequestParam(value = "prisonerId", required = false)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC"
    ) prisonerId: String?,
    @RequestParam(value = "prisonId", required = false)
    @Parameter(
      description = "Filter results by prison id/code",
      example = "MDI"
    ) prisonCode: String?,
    @RequestParam(value = "startDateTime", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or after the given timestamp",
      example = "2021-11-03T09:00:00"
    ) startDateTime: LocalDateTime?,
    @RequestParam(value = "endDateTime", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or before the given timestamp",
      example = "2021-11-03T09:00:00"
    ) endDateTime: LocalDateTime?,
    @RequestParam(value = "visitorId", required = false)
    @Parameter(
      description = "Filter results by visitor (contact id)",
      example = "12322"
    ) visitorId: Long?,
    @RequestParam(value = "visitStatus", required = true)
    @Parameter(
      description = "Filter results by visit status",
      example = "BOOKED"
    ) visitStatusList: List<VisitStatus>,
    @RequestParam(value = "page", required = true)
    @Parameter(
      description = "Pagination page number, starting at zero",
      example = "0"
    ) page: Int,
    @RequestParam(value = "size", required = true)
    @Parameter(
      description = "Pagination size per page",
      example = "50"
    ) size: Int
  ): Page<VisitDto> {
    return visitService.findVisitsByFilterPageableDescending(
      VisitFilter(
        prisonerId = prisonerId?.trim(),
        prisonCode = prisonCode?.trim(),
        startDateTime = startDateTime,
        endDateTime = endDateTime,
        visitorId = visitorId,
        visitStatusList = visitStatusList
      ),
      page,
      size
    )
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_VISIT_BY_REFERENCE)
  @Operation(
    summary = "Get a visit",
    description = "Retrieve visit by visit reference (excludes Reserved and CHANGING)",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit Information Returned"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visits for prisoner",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions retrieve a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
    ]
  )
  fun getVisitByReference(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): VisitDto {
    return visitService.getVisitByReference(reference.trim())
  }
}
