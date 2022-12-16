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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import javax.validation.Valid

const val SESSION_TEMPLATES_PATH: String = "/visit-session-templates"
const val SESSION_TEMPLATE_PATH: String = "$SESSION_TEMPLATES_PATH/template"
const val REFERENCE_SESSION_TEMPLATE_PATH: String = "$SESSION_TEMPLATE_PATH/{reference}"

@RestController
@Validated
@RequestMapping(name = "Session Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitSessionAdminController(
  private val sessionTemplateService: SessionTemplateService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(SESSION_TEMPLATES_PATH)
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
  fun getSessionTemplates(): List<SessionTemplateDto> {
    return sessionTemplateService.getSessionTemplates()
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(REFERENCE_SESSION_TEMPLATE_PATH)
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
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String,
  ): SessionTemplateDto {
    return sessionTemplateService.getSessionTemplates(reference)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(SESSION_TEMPLATE_PATH)
  @Operation(
    summary = "Create a session template",
    description = "Create a session templates",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = CreateSessionTemplateDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates created"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun createSessionTemplate(@RequestBody @Valid createSessionTemplateDto: CreateSessionTemplateDto): SessionTemplateDto {
    return sessionTemplateService.createSessionTemplate(createSessionTemplateDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(REFERENCE_SESSION_TEMPLATE_PATH)
  @Operation(
    summary = "Update a session template",
    description = "Update a session templates",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = UpdateSessionTemplateDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates updated"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun updateSessionTemplate(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String,
    @RequestBody @Valid updateSessionTemplateDto: UpdateSessionTemplateDto
  ): SessionTemplateDto {
    return sessionTemplateService.updateSessionTemplate(reference, updateSessionTemplateDto)
  }
}
