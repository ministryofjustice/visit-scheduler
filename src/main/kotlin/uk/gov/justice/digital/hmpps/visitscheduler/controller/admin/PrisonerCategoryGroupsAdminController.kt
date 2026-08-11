package uk.gov.justice.digital.hmpps.visitscheduler.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Qualifier
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
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.config.ValidationErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.CreateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.UpdateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService

const val ADMIN_CATEGORY_GROUPS_ADMIN_PATH: String = "/admin/category-groups"
const val PRISON_CATEGORY_GROUPS_ADMIN_PATH: String = "$ADMIN_CATEGORY_GROUPS_ADMIN_PATH/{prisonCode}"
const val CATEGORY_GROUP_ADMIN_PATH: String = "$ADMIN_CATEGORY_GROUPS_ADMIN_PATH/group"
const val REFERENCE_CATEGORY_GROUP_ADMIN_PATH: String = "$CATEGORY_GROUP_ADMIN_PATH/{reference}"

@RestController
@Validated
@Tag(name = "8. Category group admin rest controller")
@RequestMapping(name = "Category group resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerCategoryGroupsAdminController(
  private val sessionTemplateService: SessionTemplateService,
  @param:Qualifier("objectMapper")
  private val objectMapper: ObjectMapper,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(PRISON_CATEGORY_GROUPS_ADMIN_PATH)
  @Operation(
    summary = "Get category groups",
    description = "Get all category groups for given prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Category groups returned for given prison",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view category groups",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getCategoryGroups(
    @Schema(description = "prisonCode", example = "MDI", required = true)
    @PathVariable
    prisonCode: String,
  ): List<SessionCategoryGroupDto> = sessionTemplateService.getSessionCategoryGroups(prisonCode)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(REFERENCE_CATEGORY_GROUP_ADMIN_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get category group",
    description = "Get category group by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Category groups returned for given prison",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view category group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Category group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getCategoryGroup(
    @Schema(description = "reference", example = "afe~dcb~fc", required = true)
    @PathVariable
    reference: String,
  ): SessionCategoryGroupDto = sessionTemplateService.getSessionCategoryGroup(reference)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(CATEGORY_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Create category group",
    description = "Create category group",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Created category group",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create category group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createCategoryGroup(
    @RequestBody
    @Valid
    createCategorySessionGroup: CreateCategoryGroupDto,
  ): SessionCategoryGroupDto = sessionTemplateService.createSessionCategoryGroup(createCategorySessionGroup)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(REFERENCE_CATEGORY_GROUP_ADMIN_PATH)
  @Operation(
    summary = "Update category group",
    description = "Update existing category group by reference",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = UpdateCategoryGroupDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Updated category group",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to update category group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Category group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateCategoryGroup(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    updateCategorySessionGroup: UpdateCategoryGroupDto,
  ): SessionCategoryGroupDto = sessionTemplateService.updateSessionCategoryGroup(reference, updateCategorySessionGroup)

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @DeleteMapping(REFERENCE_CATEGORY_GROUP_ADMIN_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Delete category group",
    description = "Delete category group by reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Category group deleted",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Category group delete validation errorsvalidation errors",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ValidationErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to view category group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Session category group not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteSessionCategoryGroup(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
  ): ResponseEntity<String> {
    sessionTemplateService.deleteSessionCategoryGroup(reference)
    return ResponseEntity.status(HttpStatus.OK).body(objectMapper.writeValueAsString("Session category group Deleted $reference!"))
  }
}
