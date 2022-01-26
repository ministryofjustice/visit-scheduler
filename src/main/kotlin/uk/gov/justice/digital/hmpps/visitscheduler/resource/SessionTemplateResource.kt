package uk.gov.justice.digital.hmpps.visitscheduler.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.data.CreateSessionTemplateRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitSchedulerService
import javax.validation.Valid

@RestController
@Validated
@RequestMapping(name = "Visit Resource", path = ["/session-templates"], produces = [MediaType.APPLICATION_JSON_VALUE])
class SessionTemplateResource(
  private val visitSchedulerService: VisitSchedulerService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a session template (used to generate visit sessions)",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = CreateSessionTemplateRequest::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Session Template created"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to create a session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create a session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun createSessionTemplate(
    @RequestBody @Valid createSessionTemplateRequest: CreateSessionTemplateRequest
  ): SessionTemplateDto = visitSchedulerService.createSessionTemplate(createSessionTemplateRequest)

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @DeleteMapping("/{sessionTemplateId}")
  @Operation(
    summary = "Delete session template",
    description = "Delete a session template by id",
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
        description = "Incorrect permissions to delete a session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun deleteSessionTemplate(
    @Schema(description = "session template id", example = "45645", required = true)
    @PathVariable sessionTemplateId: Long
  ) {
    visitSchedulerService.deleteSessionTemplate(sessionTemplateId)
  }

  // convenience endpoint to retrieve all session templates to support development
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping
  @Operation(
    summary = "get session templates CONVENIENCE ENDPOINT TO SUPPORT DEV - WILL BE REPLACED",
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
  fun getSessionTemplates(): List<SessionTemplateDto> {
    return visitSchedulerService.getSessionTemplates()
  }
}
