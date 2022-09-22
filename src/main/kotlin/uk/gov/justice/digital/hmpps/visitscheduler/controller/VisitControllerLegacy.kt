package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import javax.validation.Valid
import kotlin.DeprecationLevel.WARNING

@Suppress("KotlinDeprecation")
@RestController
@Validated
@RequestMapping(name = "Visit Resource", path = ["/visits"], produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitControllerLegacy(
  private val visitService: VisitService,
  private val snsService: SnsService,
) {

  @Deprecated(message = "This endpoint should be changed to :$VISIT_RESERVE_SLOT", ReplaceWith(VISIT_RESERVE_SLOT), WARNING)
  @Suppress("KotlinDeprecation")
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = CreateVisitRequestDto::class)
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
  fun createVisit(
    @RequestBody @Valid createVisitRequest: CreateVisitRequestDto
  ): VisitDto {
    return visitService.createVisit(createVisitRequest)
  }

  @Deprecated("This endpoint should be changed to $VISIT_RESERVED_SLOT_CHANGE and to book use the $VISIT_BOOK", ReplaceWith(VISIT_RESERVED_SLOT_CHANGE, VISIT_BOOK), WARNING)
  @Suppress("KotlinDeprecation")
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping("/{reference}")
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
  fun updateVisit(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String,
    @RequestBody @Valid updateVisitRequest: UpdateVisitRequestDto
  ): VisitDto {
    val visit = visitService.updateVisit(reference.trim(), updateVisitRequest)

    // Updated to BOOKED status - review if POST & PUT are replaced with Reserve, Book & Amend endpoints
    updateVisitRequest.visitStatus?.run {
      if (visit.visitStatus == VisitStatus.BOOKED) {
        snsService.sendVisitBookedEvent(visit)
      }
    }
    return visit
  }

  @Deprecated("This endpoint should be changed to :$VISIT_CANCEL", ReplaceWith(VISIT_CANCEL), WARNING)
  @Suppress("KotlinDeprecation")
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
  ): VisitDto = visitService.cancelVisit(reference.trim(), cancelOutcome)
}
