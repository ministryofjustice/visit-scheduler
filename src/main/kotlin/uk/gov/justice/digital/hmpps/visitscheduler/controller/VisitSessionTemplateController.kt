package uk.gov.justice.digital.hmpps.visitscheduler.controller

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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService

@RestController
@Validated
@RequestMapping(name = "Session Resource", path = ["/visit-session-templates"], produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitSessionTemplateController(
  private val sessionTemplateService: SessionTemplateService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping
  @Operation(
    summary = "Get session templates",
    description = "Get all session templates",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates returned"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getSessionTemplates(): List<SessionTemplateDto> = sessionTemplateService.getSessionTemplates()

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping("/{templateId}")
  @Operation(
    summary = "Get session template",
    description = "Get all session templates",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates returned"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getSessionTemplate(
    @Schema(description = "Template id", example = "45645", required = true)
    @PathVariable templateId: Long
  ): SessionTemplateDto = sessionTemplateService.getSessionTemplates(templateId)
}
