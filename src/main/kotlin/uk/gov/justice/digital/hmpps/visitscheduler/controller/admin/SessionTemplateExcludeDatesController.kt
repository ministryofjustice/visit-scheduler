package uk.gov.justice.digital.hmpps.visitscheduler.controller.admin

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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import java.time.LocalDate

const val SESSION_TEMPLATE_EXCLUDE_DATE_PATH: String = "$SESSION_TEMPLATE_PATH/{reference}/exclude-date"

const val ADD_SESSION_TEMPLATE_EXCLUDE_DATE: String = "$SESSION_TEMPLATE_EXCLUDE_DATE_PATH/add"

const val REMOVE_SESSION_TEMPLATE_EXCLUDE_DATE: String = "$SESSION_TEMPLATE_EXCLUDE_DATE_PATH/remove"
const val GET_SESSION_TEMPLATE_EXCLUDE_DATES: String = SESSION_TEMPLATE_EXCLUDE_DATE_PATH

@RestController
@Validated
@Tag(name = "Session Template exclude dates rest controller")
@RequestMapping(name = "Session Template Exclude Dates Configuration Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class SessionTemplateExcludeDatesController(
  private val sessionTemplateService: SessionTemplateService,
) {
  @PreAuthorize("hasAnyRole('VISIT_SCHEDULER_CONFIG', 'VISIT_SCHEDULER')")
  @PutMapping(ADD_SESSION_TEMPLATE_EXCLUDE_DATE)
  @Operation(
    summary = "Add exclude date to a session.",
    description = "Add exclude date to a session.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "successfully added exclude date to a session",
      ),
      ApiResponse(
        responseCode = "400",
        description = "exclude date provided already exists for session or session can't be found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to add exclude dates to a prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun addSessionExcludeDate(
    @Schema(description = "session template reference", example = "qqw-rew-aws", required = true)
    @PathVariable
    reference: String,
    @RequestBody
    @Valid
    excludeDateDto: ExcludeDateDto,
  ): Set<LocalDate> {
    sessionTemplateService.addExcludeDate(reference, excludeDateDto)
    return sessionTemplateService.getExcludeDates(reference).map { it.excludeDate }.toSet()
  }

  @PreAuthorize("hasAnyRole('VISIT_SCHEDULER_CONFIG', 'VISIT_SCHEDULER')")
  @PutMapping(REMOVE_SESSION_TEMPLATE_EXCLUDE_DATE)
  @Operation(
    summary = "Remove exclude date for a session template.",
    description = "Remove exclude date for a session template.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "successfully removed exclude date for a session template",
      ),
      ApiResponse(
        responseCode = "400",
        description = "exclude date does not exist for session template or session template can't be found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to remove exclude date for a session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun removeSessionTemplateExcludeDate(
    @Schema(description = "prison id", example = "BHI", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    excludeDateDto: ExcludeDateDto,
  ): Set<LocalDate> {
    sessionTemplateService.removeExcludeDate(reference, excludeDateDto)
    return sessionTemplateService.getExcludeDates(reference).map { it.excludeDate }.toSet()
  }

  @PreAuthorize("hasAnyRole('VISIT_SCHEDULER_CONFIG', 'VISIT_SCHEDULER')")
  @GetMapping(GET_SESSION_TEMPLATE_EXCLUDE_DATES)
  @Operation(
    summary = "Get exclude dates for a session template.",
    description = "Get exclude dates for a session template.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "session template's exclude dates returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get exclude dates for a session template",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonExcludeDates(
    @Schema(description = "session template reference", example = "abc-def-ghi", required = true)
    @PathVariable
    reference: String,
  ): List<ExcludeDateDto> = sessionTemplateService.getExcludeDates(reference)
}
