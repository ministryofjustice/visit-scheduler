package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import java.time.DayOfWeek
import java.time.LocalDate

const val SESSION_TEMPLATES_PATH: String = "/visit-session-templates"
const val SESSION_TEMPLATE_PATH: String = "$SESSION_TEMPLATES_PATH/template"
const val REFERENCE_SESSION_TEMPLATE_PATH: String = "$SESSION_TEMPLATE_PATH/{reference}"

@RestController
@Validated
@RequestMapping(name = "Session Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "4. Session admin rest controller")
class VisitSessionAdminController(
  private val sessionTemplateService: SessionTemplateService,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(SESSION_TEMPLATES_PATH)
  @Operation(
    summary = "Get session templates",
    description = "Get session templates by given parameters",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSessionTemplates(
    @RequestParam(value = "prisonCode", required = true)
    @Parameter(
      description = "Filter results by prison id/code",
      example = "MDI",
    )
    prisonCode: String,
    @RequestParam(value = "dayOfWeek", required = false)
    @Parameter(
      description = "Filter results by day of week",
      example = "MONDAY",
    )
    dayOfWeek: DayOfWeek?,
    @RequestParam(value = "validFrom", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by that when the session template is valid from",
      example = "2021-11-03",
    )
    rangeStartDate: LocalDate?,
    @RequestParam(value = "validTo", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by that when the session template is valid to",
      example = "2021-11-03",
    )
    rangeEndDate: LocalDate?,
  ): List<SessionTemplateDto> {
    return sessionTemplateService.getSessionTemplates(prisonCode, dayOfWeek, rangeStartDate, rangeEndDate)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(REFERENCE_SESSION_TEMPLATE_PATH)
  @Operation(
    summary = "Get session template",
    description = "Get session template by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Session Template not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSessionTemplate(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): SessionTemplateDto {
    return sessionTemplateService.getSessionTemplates(reference)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @DeleteMapping(REFERENCE_SESSION_TEMPLATE_PATH)
  @Operation(
    summary = "Delete session template by reference",
    description = "Delete session template by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates deleted",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Session Template not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteSessionTemplate(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): ResponseEntity<String> {
    sessionTemplateService.deleteSessionTemplates(reference)
    return ResponseEntity.status(HttpStatus.OK).body("Session Template Deleted $reference!")
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
          schema = Schema(implementation = CreateSessionTemplateDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates created",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createSessionTemplate(
    @RequestBody @Valid
    createSessionTemplateDto: CreateSessionTemplateDto,
  ): SessionTemplateDto {
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
          schema = Schema(implementation = UpdateSessionTemplateDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates updated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Session Template not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateSessionTemplate(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    updateSessionTemplateDto: UpdateSessionTemplateDto,
  ): SessionTemplateDto {
    return sessionTemplateService.updateSessionTemplate(reference, updateSessionTemplateDto)
  }
}
