package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.ApplicationService

const val APPLICATION_CONTROLLER_PATH: String = "/visits/application"
const val APPLICATION_RESERVE_SLOT: String = "$APPLICATION_CONTROLLER_PATH/slot/reserve"
const val APPLICATION_RESERVED_SLOT_CHANGE: String = "$APPLICATION_CONTROLLER_PATH/{reference}/slot/change"
const val APPLICATION_CHANGE: String = "$APPLICATION_CONTROLLER_PATH/{reference}/change"

@RestController
@Validated
@Tag(name = "Application rest controller")
@RequestMapping(name = "Application Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class ApplicationController(
  private val applicationService: ApplicationService,
) {
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(APPLICATION_RESERVE_SLOT)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create an initial application and reserve a slot",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = SessionSlotDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit slot reserved",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to reserve a slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to reserve a slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createInitialApplication(
    @RequestBody @Valid
    sessionSlotDto: SessionSlotDto,
  ): ApplicationDto {
    return applicationService.createInitialApplication(sessionSlotDto = sessionSlotDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(APPLICATION_RESERVED_SLOT_CHANGE)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Change an incomplete application",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = ChangeVisitSlotRequestDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit slot changed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to changed a visit slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to changed a visit slot",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit slot not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun changeIncompleteApplication(
    @Schema(description = "reference", example = "dfs-wjs-eqr", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    changeVisitSlotRequestDto: ChangeVisitSlotRequestDto,
  ): ApplicationDto {
    return applicationService.changeIncompleteApplication(reference.trim(), changeVisitSlotRequestDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(APPLICATION_CHANGE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create an application for an existing visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = SessionSlotDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit created",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to change a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to change a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createApplicationForAnExistingVisit(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    sessionSlotDto: SessionSlotDto,
  ): ApplicationDto {
    return applicationService.createApplicationForAnExistingVisit(reference.trim(), sessionSlotDto)
  }
}
