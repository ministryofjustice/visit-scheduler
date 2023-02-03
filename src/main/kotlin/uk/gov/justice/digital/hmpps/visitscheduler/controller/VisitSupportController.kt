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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SupportTypeDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SupportService

@RestController
@Validated
@RequestMapping(name = "Support Resource", path = ["/visit-support"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "3. Visit support rest controller")
class VisitSupportController(
  private val supportService: SupportService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping
  @Operation(
    summary = "Available Support",
    description = "Retrieve all available support types",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Available Support information returned"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get Available Support",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getSupportTypes(): List<SupportTypeDto> = supportService.getSupportTypes()
}
