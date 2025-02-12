package uk.gov.justice.digital.hmpps.visitscheduler.controller.admin

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.config.ValidationErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.CreateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.UpdateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService

const val ADMIN_INCENTIVE_GROUPS_ADMIN_PATH: String = "/admin/incentive-groups"
const val PRISON_INCENTIVE_GROUPS_ADMIN_PATH: String = "$ADMIN_INCENTIVE_GROUPS_ADMIN_PATH/{prisonCode}"
const val INCENTIVE_GROUP_ADMIN_PATH: String = "$ADMIN_INCENTIVE_GROUPS_ADMIN_PATH/group"
const val REFERENCE_INCENTIVE_GROUP_ADMIN_PATH: String = "$INCENTIVE_GROUP_ADMIN_PATH/{reference}"

@RestController
@Validated
@Tag(name = "9. Incentive group admin rest controller")
@RequestMapping(name = "Incentive group resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerIncentiveGroupsAdminController(
  private val objectMapper: ObjectMapper,
  private val sessionTemplateService: SessionTemplateService,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(PRISON_INCENTIVE_GROUPS_ADMIN_PATH)
  @Operation(
    summary = "Get incentive groups",
    description = "Get all incentive groups for given prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive groups returned for given prison",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view incentive groups",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getIncentiveGroups(
    @Schema(description = "prisonCode", example = "MDI", required = true)
    @PathVariable
    prisonCode: String,
  ): List<SessionIncentiveLevelGroupDto> = sessionTemplateService.getSessionIncentiveGroups(prisonCode)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(REFERENCE_INCENTIVE_GROUP_ADMIN_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get incentive group",
    description = "Get incentive group by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive group returned for given reference",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view incentive group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Incentive group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getIncentiveGroup(
    @Schema(description = "reference", example = "afe~dcb~fc", required = true)
    @PathVariable
    reference: String,
  ): SessionIncentiveLevelGroupDto = sessionTemplateService.getSessionIncentiveGroup(reference)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(INCENTIVE_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Create incentive group",
    description = "Create incentive group",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Created incentive group",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create incentive group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createIncentiveGroup(
    @RequestBody
    @Valid
    createIncentiveSessionGroup: CreateIncentiveGroupDto,
  ): SessionIncentiveLevelGroupDto = sessionTemplateService.createSessionIncentiveGroup(createIncentiveSessionGroup)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(REFERENCE_INCENTIVE_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Update incentive group",
    description = "Update existing incentive group by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Updated incentive group",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update incentive group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Incentive group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateIncentiveGroup(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    updateIncentiveSessionGroup: UpdateIncentiveGroupDto,
  ): SessionIncentiveLevelGroupDto = sessionTemplateService.updateSessionIncentiveGroup(reference, updateIncentiveSessionGroup)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @DeleteMapping(REFERENCE_INCENTIVE_GROUP_ADMIN_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Delete incentive group",
    description = "Delete incentive group by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive group deleted",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incentive group delete validation errors",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ValidationErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view  incentive group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Session incentive group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteSessionIncentiveGroup(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): ResponseEntity<String> {
    sessionTemplateService.deleteSessionIncentiveGroup(reference)
    return ResponseEntity.status(HttpStatus.OK).body(objectMapper.writeValueAsString("Session incentive group Deleted $reference!"))
  }
}
