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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import javax.validation.Valid

const val LOCATION_GROUPS_ADMIN_PATH: String = "/location-groups"
const val LOCATION_GROUP_ADMIN_PATH: String = "$LOCATION_GROUPS_ADMIN_PATH/group"
const val REFERENCE_LOCATION_GROUP_ADMIN_PATH: String = "$LOCATION_GROUP_ADMIN_PATH/{reference}"

@RestController
@Validated
@RequestMapping(name = "Location group resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class LocationGroupAdminController(
  private val sessionTemplateService: SessionTemplateService
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(LOCATION_GROUPS_ADMIN_PATH)
  @Operation(
    summary = "Get location groups",
    description = "Get all location groups for given prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Location groups returned for given prison"
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
  fun getLocationGroups(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Filter results by prison id/code",
      example = "MDI"
    ) prisonCode: String,
  ): List<SessionLocationGroupDto> {
    return sessionTemplateService.getSessionLocationGroup(prisonCode)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(LOCATION_GROUP_ADMIN_PATH)
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
  @PutMapping(REFERENCE_LOCATION_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Update location group",
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
        description = "Updated location group"
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update location group",
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
}
