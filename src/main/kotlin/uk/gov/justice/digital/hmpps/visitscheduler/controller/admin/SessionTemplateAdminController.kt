package uk.gov.justice.digital.hmpps.visitscheduler.controller.admin

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.visitscheduler.config.ValidationErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.MoveVisitsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.RequestSessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService

const val ADMIN_SESSION_TEMPLATES_PATH: String = "/admin/session-templates"
const val SESSION_TEMPLATE_PATH: String = "$ADMIN_SESSION_TEMPLATES_PATH/template"
const val FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE: String = "$SESSION_TEMPLATE_PATH/matching/"
const val FIND_MATCHING_SESSION_TEMPLATES_ON_UPDATE: String = "$SESSION_TEMPLATE_PATH/{reference}/matching/"
const val MOVE_VISITS: String = "$ADMIN_SESSION_TEMPLATES_PATH/move/"
const val REFERENCE_SESSION_TEMPLATE_PATH: String = "$SESSION_TEMPLATE_PATH/{reference}"
const val SESSION_TEMPLATE_VISIT_STATS: String = "$SESSION_TEMPLATE_PATH/{reference}/stats"
const val ACTIVATE_SESSION_TEMPLATE: String = "$SESSION_TEMPLATE_PATH/{reference}/activate"
const val DEACTIVATE_SESSION_TEMPLATE: String = "$SESSION_TEMPLATE_PATH/{reference}/deactivate"
const val ACTIVATE_SESSION_TEMPLATE_CLIENT: String = "$REFERENCE_SESSION_TEMPLATE_PATH/client/{type}/activate"
const val DEACTIVATE_SESSION_TEMPLATE_CLIENT: String = "$REFERENCE_SESSION_TEMPLATE_PATH/client/{type}/deactivate"

enum class SessionTemplateRangeType {
  CURRENT_OR_FUTURE,
  HISTORIC,
  ALL,
}

@RestController
@Validated
@RequestMapping(name = "Session Template Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "6. Session template admin rest controller")
class SessionTemplateAdminController(
  private val sessionTemplateService: SessionTemplateService,
  private val objectMapper: ObjectMapper,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(ADMIN_SESSION_TEMPLATES_PATH)
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
    @RequestParam(value = "rangeType", required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filters session templates depending on their from and to Date",
      example = "CURRENT_OR_FUTURE",
    )
    rangeType: SessionTemplateRangeType,
  ): List<SessionTemplateDto> = sessionTemplateService.getSessionTemplates(prisonCode, rangeType)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
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
  ): SessionTemplateDto = sessionTemplateService.getSessionTemplates(reference)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
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
    @RequestBody
    @Valid
    createSessionTemplateDto: CreateSessionTemplateDto,
  ): SessionTemplateDto = sessionTemplateService.createSessionTemplate(createSessionTemplateDto)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
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
        responseCode = "400",
        description = "Session Template update validation errors",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ValidationErrorResponse::class))],
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
  ): SessionTemplateDto = sessionTemplateService.updateSessionTemplate(reference, updateSessionTemplateDto)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @DeleteMapping(REFERENCE_SESSION_TEMPLATE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Delete session template by reference",
    description = "Delete session template by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session templates deleted",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Session Template delete validation errors",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ValidationErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to delete session templates",
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
    sessionTemplateService.deleteSessionTemplate(reference)
    return ResponseEntity.status(HttpStatus.OK).body(objectMapper.writeValueAsString("Session Template Deleted $reference!"))
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(ACTIVATE_SESSION_TEMPLATE)
  @Operation(
    summary = "Activate session template using given session template reference",
    description = "Activate session template using given session template reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "session template activated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to activate session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "session template can't be found to activate",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun activateSessionTemplate(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): SessionTemplateDto = sessionTemplateService.activateSessionTemplate(reference)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(DEACTIVATE_SESSION_TEMPLATE)
  @Operation(
    summary = "Deactivate session template using given session template reference",
    description = "Deactivate session template using given session template reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session template deactivated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to deactivate session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "session template can't be found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deActivateSessionTemplate(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): SessionTemplateDto = sessionTemplateService.deActivateSessionTemplate(reference)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(SESSION_TEMPLATE_VISIT_STATS)
  @Operation(
    summary = "Get session template visits stats using given session template reference",
    description = "Session template visits stats using given session template reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session template visits stats",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get Session template visits stats",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "session template can't be found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSessionTemplateVisitStats(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    requestSessionTemplateVisitStatsDto: RequestSessionTemplateVisitStatsDto,
  ): SessionTemplateVisitStatsDto = sessionTemplateService.getSessionTemplateVisitStats(reference, requestSessionTemplateVisitStatsDto)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE)
  @Operation(
    summary = "Get matching session templates",
    description = "Get matching session templates",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "List of clashing session templates or empty list if no matches found",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get matching session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Invalid request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getMatchingSessionTemplatesOnCreate(
    @RequestBody @Valid
    createSessionTemplateDto: CreateSessionTemplateDto,
  ): List<String> = sessionTemplateService.hasMatchingSessionTemplates(createSessionTemplateDto)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(FIND_MATCHING_SESSION_TEMPLATES_ON_UPDATE)
  @Operation(
    summary = "Get matching session templates",
    description = "Get matching session templates",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "List of clashing session templates or empty list if no matches found",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get matching session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Invalid request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getMatchingSessionTemplatesOnUpdate(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    updateSessionTemplateDto: UpdateSessionTemplateDto,
  ): List<String> = sessionTemplateService.hasMatchingSessionTemplates(reference, updateSessionTemplateDto)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(MOVE_VISITS)
  @Operation(
    summary = "Move visits from 1 session template to another.",
    description = "Move visits from 1 session template to another if the new session template has same details as the current one.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Number of visits migrated on successful switching of session template.",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid session reference passed.",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get matching session templates",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Unable to move visits to session template for reasons detailed",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ValidationErrorResponse::class))],
      ),
    ],
  )
  fun moveVisits(
    @RequestBody @Valid
    moveVisitsDto: MoveVisitsDto,
  ): Int = sessionTemplateService.moveSessionTemplateVisits(moveVisitsDto.fromSessionTemplateReference, moveVisitsDto.toSessionTemplateReference, moveVisitsDto.fromDate)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(ACTIVATE_SESSION_TEMPLATE_CLIENT)
  @Operation(
    summary = "Activate session template client using given session template reference and client type",
    description = "Activate a session template for a user client type",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "session template client activated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to activate prison client",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "session template cannot be found to activate session template client",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun activateSessionTemplateForClient(
    @Schema(description = "session template reference", example = "aaa-bbb-ccc", required = true)
    @PathVariable
    reference: String,
    @Schema(description = "type", example = "STAFF", required = true)
    @PathVariable
    type: UserType,
  ): UserClientDto = sessionTemplateService.activateSessionTemplateClient(reference, type)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(DEACTIVATE_SESSION_TEMPLATE_CLIENT)
  @Operation(
    summary = "Deactivate session template client using given session template reference and client type",
    description = "Deactivate a session template for a user client type",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "session template client deactivated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to activate prison client",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "session template cannot be found to deactivate session template client",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deActivateSessionTemplateClient(
    @Schema(description = "session template reference", example = "aaa-bbb-ccc", required = true)
    @PathVariable
    reference: String,
    @Schema(description = "type", example = "STAFF", required = true)
    @PathVariable
    type: UserType,
  ): UserClientDto = sessionTemplateService.deActivateSessionTemplateClient(reference, type)
}
