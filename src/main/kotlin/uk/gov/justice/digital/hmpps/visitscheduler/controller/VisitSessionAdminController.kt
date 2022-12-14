package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import javax.validation.Valid

const val SESSION_TEMPLATES_PATH: String = "/visit-session-templates"
const val SESSION_TEMPLATE: String = "$SESSION_TEMPLATES_PATH/{templateId}"
const val SESSION_TEMPLATE_LOCATION_GROUPS: String = "$SESSION_TEMPLATES_PATH/location/groups"

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
  @GetMapping(SESSION_TEMPLATE)
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
  ): SessionTemplateDto {
    return sessionTemplateService.getSessionTemplates(templateId)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(SESSION_TEMPLATE_LOCATION_GROUPS)
  @Operation(
    summary = "Get session templates location groups",
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
  fun getSessionTemplateLocationGroups(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Filter results by prison id/code",
      example = "MDI"
    ) prisonCode: String,
  ): List<SessionLocationGroupDto> {
    return sessionTemplateService.getSessionLocationGroup(prisonCode)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(SESSION_TEMPLATE_LOCATION_GROUPS)
  @Operation(
    summary = "Create location group",
    description = "Create location group",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = SessionLocationGroupDto::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Created location group"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create location group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun createLocationGroup(
    @RequestBody @Valid createLocationSessionGroup: CreateLocationGroupDto
  ): SessionLocationGroupDto {
    return sessionTemplateService.createSessionLocationGroup(createLocationSessionGroup)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PatchMapping(SESSION_TEMPLATE_LOCATION_GROUPS)
  @Operation(
    summary = "Update session location group",
    description = "Update existing location group",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = SessionLocationGroupDto::class)
        )
      ]
    ),
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
  fun updateLocationGroup(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String,
    @RequestBody @Valid updateLocationSessionGroup: UpdateLocationGroupDto
  ): SessionLocationGroupDto {
    return sessionTemplateService.updateSessionLocationGroup(reference, updateLocationSessionGroup)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(SESSION_TEMPLATES_PATH)
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
  @PatchMapping(SESSION_TEMPLATES_PATH)
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
  fun updateSessionTemplate(@RequestBody @Valid updateSessionTemplateDto: UpdateSessionTemplateDto): SessionTemplateDto {
    return sessionTemplateService.updateSessionTemplate(updateSessionTemplateDto)
  }
}
