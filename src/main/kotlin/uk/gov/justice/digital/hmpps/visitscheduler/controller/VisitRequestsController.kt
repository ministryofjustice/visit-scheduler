package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveRejectionVisitRequestBodyDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestSummaryDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestsCountDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitRequestsService

const val VISIT_REQUESTS_CONTROLLER_PATH: String = "/visits/requests"

const val VISIT_REQUESTS_VISITS_FOR_PRISON_PATH: String = "$VISIT_REQUESTS_CONTROLLER_PATH/{prisonCode}"
const val VISIT_REQUESTS_COUNT_FOR_PRISON_PATH: String = "$VISIT_REQUESTS_CONTROLLER_PATH/{prisonCode}/count"

const val VISIT_REQUESTS_APPROVE_VISIT_BY_REFERENCE_PATH: String = "$VISIT_REQUESTS_CONTROLLER_PATH/{reference}/approve"
const val VISIT_REQUESTS_REJECT_VISIT_BY_REFERENCE_PATH: String = "$VISIT_REQUESTS_CONTROLLER_PATH/{reference}/reject"

@RestController
@Validated
@Tag(name = "Visit request controller $VISIT_REQUESTS_CONTROLLER_PATH")
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitRequestsController(private val visitRequestsService: VisitRequestsService) {
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(VISIT_REQUESTS_COUNT_FOR_PRISON_PATH)
  @Operation(
    summary = "Get count for how many visit requests are open for a prison",
    description = "Returns an Int count for how many visit requests are open for a prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved count of visit requests for prison",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitRequestsCountForPrison(
    @Schema(description = "prisonCode", example = "CFI", required = true)
    @PathVariable
    prisonCode: String,
  ): VisitRequestsCountDto = VisitRequestsCountDto(visitRequestsService.getVisitRequestsCountForPrison(prisonCode))

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(VISIT_REQUESTS_VISITS_FOR_PRISON_PATH)
  @Operation(
    summary = "Get all visit requests for a prison",
    description = "Retrieve a list of visit requests for a prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved all visit requests for a prison",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitRequestsForPrison(
    @Schema(description = "prisonCode", example = "CFI", required = true)
    @PathVariable
    prisonCode: String,
  ): List<VisitRequestSummaryDto> = visitRequestsService.getVisitRequestsForPrison(prisonCode)

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_REQUESTS_APPROVE_VISIT_BY_REFERENCE_PATH)
  @Operation(
    summary = "Approve a visit request",
    description = "Endpoint to approve a visit request by visit reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Successfully approved visit request",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to approve visit request by reference (not found or not in correct sub status)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun approveVisitRequestByReference(
    @Schema(description = "visit reference", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    approveRejectionVisitRequestBodyDto: ApproveRejectionVisitRequestBodyDto,
  ): VisitDto = visitRequestsService.approveOrRejectVisitRequestByReference(approveRejectionVisitRequestBodyDto, isApproved = true)

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_REQUESTS_REJECT_VISIT_BY_REFERENCE_PATH)
  @Operation(
    summary = "Reject a visit request",
    description = "Endpoint to reject a visit request by visit reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Successfully rejected visit request",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to reject visit request by reference (not found or not in correct sub status)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun rejectVisitRequestByReference(
    @Schema(description = "visit reference", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    approveRejectionVisitRequestBodyDto: ApproveRejectionVisitRequestBodyDto,
  ): VisitDto = visitRequestsService.approveOrRejectVisitRequestByReference(approveRejectionVisitRequestBodyDto, isApproved = false)
}
