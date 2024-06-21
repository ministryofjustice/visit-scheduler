package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService

const val GET_FUTURE_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE: String = "/public/booker/{bookerReference}/visits/booked/future"
const val GET_CANCELLED_PUBLIC_VISITS_BY_BOOKER_REFERENCE: String = "/public/booker/{bookerReference}/visits/cancelled"
const val GET_PAST_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE: String = "/public/booker/{bookerReference}/visits/booked/past"

@RestController
@Validated
@Tag(name = " Public visit rest controller")
@RequestMapping(name = "Public visit Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class PublicVisitController(
  private val visitService: VisitService,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_FUTURE_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE)
  @Operation(
    summary = "Get future public booked visits by booker reference",
    description = "Get future public booked visits by booker reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Future public booked visits returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get future booked visits by booker reference",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getFuturePublicBookedVisitsByBookerReference(
    @Schema(description = "bookerReference", example = "asd-aed-vhj", required = true)
    @PathVariable
    bookerReference: String,
  ): List<VisitDto> {
    return visitService.getFuturePublicBookedVisitsByBookerReference(bookerReference)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_CANCELLED_PUBLIC_VISITS_BY_BOOKER_REFERENCE)
  @Operation(
    summary = "Get public cancelled visits by booker reference",
    description = "Get public cancelled visits by booker reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "cancelled public visits returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get cancelled public visits by booker reference",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPublicCanceledVisitsByBookerReference(
    @Schema(description = "bookerReference", example = "asd-aed-vhj", required = true)
    @PathVariable
    bookerReference: String,
  ): List<VisitDto> {
    return visitService.getPublicCanceledVisitsByBookerReference(bookerReference)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_PAST_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE)
  @Operation(
    summary = "Get public past visits by booker reference",
    description = "Get public past visits by booker reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "past public visits returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get past public visits by booker reference",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPublicPastVisitsByBookerReference(
    @Schema(description = "bookerReference", example = "asd-aed-vhj", required = true)
    @PathVariable
    bookerReference: String,
  ): List<VisitDto> {
    return visitService.getPublicPastVisitsByBookerReference(bookerReference)
  }
}
