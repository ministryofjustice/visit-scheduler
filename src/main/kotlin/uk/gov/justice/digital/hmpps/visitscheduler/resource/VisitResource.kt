package uk.gov.justice.digital.hmpps.visitscheduler.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.data.CreateVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.filter.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitSchedulerService
import java.time.LocalDateTime
import javax.validation.Valid
import javax.validation.constraints.NotBlank

@RestController
@Validated
@RequestMapping(name = "Visit Resource", path = ["/visits"], produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitResource(
  private val visitSchedulerService: VisitSchedulerService
) {

  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @GetMapping("/{visitId}")
  @Operation(
    summary = "Get visit",
    description = "Retrieve visit by visit id",
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
  fun getVisitById(
    @Schema(description = "visit id", example = "45645", required = true)
    @PathVariable visitId: Long
  ): VisitDto =
    visitSchedulerService.getVisitById(visitId)

  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @GetMapping
  @Operation(
    summary = "Get visits",
    description = "Retrieve visits with optional filters",
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
    @RequestParam(value = "contactId", required = false)
    @Parameter(
      description = "Filter results by visitor (contact id)",
      example = "12322"
    ) contactId: Long?
  ): List<VisitDto> =
    visitSchedulerService.findVisitsByFilter(
      VisitFilter(
        prisonerId = prisonerId,
        prisonId = prisonId,
        startDateTime = startTimestamp,
        endDateTime = endTimestamp,
        contactId = contactId
      )
    )

  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = CreateVisitRequest::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Local Admin user information returned"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to create user information",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create a user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun createVisit(
    @RequestBody @Valid createVisitRequest: CreateVisitRequest
  ): VisitDto = visitSchedulerService.createVisit(createVisitRequest)

  @PreAuthorize("hasRole('ROLE_VISIT_SCHEDULER')")
  @DeleteMapping("/{visitId}")
  @Operation(
    summary = "Delete visit",
    description = "Delete a visit by visit id",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit deleted"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to delete a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun deleteVisit(
    @Schema(description = "visit id", example = "45645", required = true)
    @PathVariable visitId: Long
  ) {
    visitSchedulerService.deleteVisit(visitId)
  }
}

@JsonInclude(NON_NULL)
@Schema(description = "Visit")
data class VisitDto(
  @Schema(description = "Visit id", example = "123", required = true) val id: Long,
  @Schema(description = "prisonerId", example = "AF34567G", required = true) val prisonerId: String,
  @Schema(description = "prisonId", example = "MDI", required = true) val prisonId: String,
  @Schema(description = "visitRoom", example = "A1 L3", required = true) val visitRoom: String,
  @Schema(description = "visitType", example = "STANDARD_SOCIAL", required = true) val visitType: String,
  @Schema(description = "visitTypeDescription", example = "Standard Social", required = true) val visitTypeDescription: String,
  @Schema(description = "status", example = "RESERVED", required = true) val status: String,
  @Schema(description = "statusDescription", example = "Reserved", required = true) val statusDescription: String,
  @Schema(
    description = "The date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotBlank val startTimestamp: LocalDateTime,
  @Schema(
    description = "The finishing date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotBlank val endTimestamp: LocalDateTime
) {

  constructor(visitEntity: Visit) : this(
    id = visitEntity.id,
    prisonerId = visitEntity.prisonerId,
    prisonId = visitEntity.prisonId,
    startTimestamp = visitEntity.visitStart,
    endTimestamp = visitEntity.visitEnd,
    status = visitEntity.status.name,
    statusDescription = visitEntity.status.description,
    visitRoom = visitEntity.visitRoom,
    visitType = visitEntity.visitType.name,
    visitTypeDescription = visitEntity.visitType.description,
  )
}
