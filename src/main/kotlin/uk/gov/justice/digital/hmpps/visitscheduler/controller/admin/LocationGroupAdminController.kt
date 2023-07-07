package uk.gov.justice.digital.hmpps.visitscheduler.controller.admin

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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService

const val ADMIN_LOCATION_GROUPS_ADMIN_PATH: String = "/admin/location-groups"
const val PRISON_LOCATION_GROUPS_ADMIN_PATH: String = "$ADMIN_LOCATION_GROUPS_ADMIN_PATH/{prisonCode}"
const val LOCATION_GROUP_ADMIN_PATH: String = "$ADMIN_LOCATION_GROUPS_ADMIN_PATH/group"
const val REFERENCE_LOCATION_GROUP_ADMIN_PATH: String = "$LOCATION_GROUP_ADMIN_PATH/{reference}"

@RestController
@Validated
@Tag(name = "7. Location group admin rest controller")
@RequestMapping(name = "Location group resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class LocationGroupAdminController(
  private val sessionTemplateService: SessionTemplateService,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(PRISON_LOCATION_GROUPS_ADMIN_PATH)
  @Operation(
    summary = "Get location groups",
    description = "Get all location groups for given prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Location groups returned for given prison",
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
  fun getLocationGroups(
    @Schema(description = "prisonCode", example = "MDI", required = true)
    @PathVariable
    prisonCode: String,
  ): List<SessionLocationGroupDto> {
    return sessionTemplateService.getSessionLocationGroups(prisonCode)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(REFERENCE_LOCATION_GROUP_ADMIN_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get location group",
    description = "Get location group by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Location groups returned for given prison",
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
        description = "Location group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getLocationGroup(
    @Schema(description = "reference", example = "afe~dcb~fc", required = true)
    @PathVariable
    reference: String,
  ): SessionLocationGroupDto {
    return sessionTemplateService.getSessionLocationGroup(reference)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(LOCATION_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Create location group",
    description = "Create location group",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = CreateLocationGroupDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Created location group",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create location group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createLocationGroup(
    @RequestBody
    @Valid
    createLocationSessionGroup: CreateLocationGroupDto,
  ): SessionLocationGroupDto {
    return sessionTemplateService.createSessionLocationGroup(createLocationSessionGroup)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @DeleteMapping(REFERENCE_LOCATION_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Delete location group",
    description = "Delete location group by reference",
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
        description = "Session location group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteSessionLocationGroup(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): ResponseEntity<String> {
    sessionTemplateService.deleteSessionLocationGroup(reference)
    return ResponseEntity.status(HttpStatus.OK).body("Session location group Deleted $reference!")
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(REFERENCE_LOCATION_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Update location group",
    description = "Update existing location group by reference",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = UpdateLocationGroupDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Updated location group",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update location group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Location group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateLocationGroup(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    updateLocationSessionGroup: UpdateLocationGroupDto,
  ): SessionLocationGroupDto {
    return sessionTemplateService.updateSessionLocationGroup(reference, updateLocationSessionGroup)
  }
}
