package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDateTime
import javax.validation.Valid

@RestController
@Validated
@RequestMapping(name = "Visit Resource", path = ["/visits"], produces = [MediaType.APPLICATION_JSON_VALUE])
class createReservationRequester(
  private val visitService: VisitService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping("/reserve")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = ReserveVisitRequestDto::class)
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
        description = "Incorrect request to create a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun reserveVisit(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @RequestBody @Valid reservedVisitRequestDto: ReserveVisitRequestDto
  ): VisitDto {
    return visitService.reserveVisit(reservedVisitRequestDto = reservedVisitRequestDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping("/{reference}/reserve")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = ReserveVisitRequestDto::class)
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
        description = "Incorrect request to create a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun reserveVisitWithReference(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String,
    @RequestBody @Valid reservedVisitRequestDto: ReserveVisitRequestDto
  ): VisitDto {
    return visitService.reserveVisit(reference.trim(), reservedVisitRequestDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping("/{reference}/update/reservation")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Update an existing visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = UpdateVisitRequestDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit updated"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to update a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
    ]
  )
  fun updateReservation(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = false)
    @PathVariable reference: String,
    @RequestBody @Valid updateVisitRequest: UpdateVisitRequestDto
  ): VisitDto {
    return visitService.updateReservation(reference.trim(), updateVisitRequest)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping("/{reference}/book")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Update an existing visit",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit updated"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to update a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update a visit",
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
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): VisitDto {
    return visitService.bookVisit(reference.trim())
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PatchMapping("/{reference}/cancel")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Cancel an existing visit",
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

  @Suppress("KotlinDeprecation")
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping
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
  fun getVisitsByFilter(
    @RequestParam(value = "prisonerId", required = false)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC"
    ) prisonerId: String?,
    @RequestParam(value = "prisonId", required = false)
    @Parameter(
      description = "Filter results by prison id",
      example = "MDI"
    ) prisonId: String?,
    @RequestParam(value = "startTimestamp", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or after the given timestamp",
      example = "2021-11-03T09:00:00"
    ) startTimestamp: LocalDateTime?,
    @RequestParam(value = "endTimestamp", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or before the given timestamp",
      example = "2021-11-03T09:00:00"
    ) endTimestamp: LocalDateTime?,
    @RequestParam(value = "nomisPersonId", required = false)
    @Parameter(
      description = "Filter results by visitor (contact id)",
      example = "12322"
    ) nomisPersonId: Long?,
    @RequestParam(value = "visitStatus", required = false)
    @Parameter(
      description = "Filter results by visit status",
      example = "BOOKED"
    ) visitStatus: VisitStatus?
  ): List<VisitDto> =
    visitService.findVisitsByFilter(
      VisitFilter(
        prisonerId = prisonerId?.trim(),
        prisonId = prisonId?.trim(),
        startDateTime = startTimestamp,
        endDateTime = endTimestamp,
        nomisPersonId = nomisPersonId,
        visitStatus = visitStatus
      )
    )

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(params = ["page", "size"])
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
      description = "Filter results by prison id",
      example = "MDI"
    ) prisonId: String?,
    @RequestParam(value = "startTimestamp", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or after the given timestamp",
      example = "2021-11-03T09:00:00"
    ) startTimestamp: LocalDateTime?,
    @RequestParam(value = "endTimestamp", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or before the given timestamp",
      example = "2021-11-03T09:00:00"
    ) endTimestamp: LocalDateTime?,
    @RequestParam(value = "nomisPersonId", required = false)
    @Parameter(
      description = "Filter results by visitor (contact id)",
      example = "12322"
    ) nomisPersonId: Long?,
    @RequestParam(value = "visitStatus", required = false)
    @Parameter(
      description = "Filter results by visit status",
      example = "BOOKED"
    ) visitStatus: VisitStatus?,
    @RequestParam(value = "page", required = true)
    @Parameter(
      description = "Pagination page number, starting at zero",
      example = "0"
    ) page: Int?,
    @RequestParam(value = "size", required = true)
    @Parameter(
      description = "Pagination size per page",
      example = "50"
    ) size: Int?
  ): Page<VisitDto> =
    visitService.findVisitsByFilterPageableDescending(
      VisitFilter(
        prisonerId = prisonerId?.trim(),
        prisonId = prisonId?.trim(),
        startDateTime = startTimestamp,
        endDateTime = endTimestamp,
        nomisPersonId = nomisPersonId,
        visitStatus = visitStatus
      ),
      page,
      size
    )

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping("/{reference}")
  @Operation(
    summary = "Get visit",
    description = "Retrieve visit by visit reference",
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
