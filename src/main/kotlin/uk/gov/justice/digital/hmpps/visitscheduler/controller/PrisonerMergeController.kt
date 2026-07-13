package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerMergeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerMergeNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerMergeBulkService
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerMergeService

const val VISIT_NOTIFICATION_PRISONER_MERGE_PATH: String = "$VISIT_CONTROLLER_PATH/prisoner/merge"
const val VISIT_NOTIFICATION_PRISONER_MERGE_BATCH_PATH: String = "$VISIT_NOTIFICATION_PRISONER_MERGE_PATH/batch"

@RestController
@Validated
@Tag(name = "Merge prisoner controller $VISIT_NOTIFICATION_PRISONER_MERGE_PATH")
@RequestMapping(name = "Visit notification Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerMergeController(
  private val prisonerMergeService: PrisonerMergeService,
  private val prisonerMergeBulkService: PrisonerMergeBulkService,
) {
  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_PRISONER_MERGE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Endpoint to handle a prisoner merge event.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner merge has completed successfully",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to notify VSiP of a prisoner merge.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to notify VSiP of a prisoner merge.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun notifyVSiPOfPrisonerMerge(
    @RequestBody @Valid
    dto: PrisonerMergeNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPOfPrisonerMerge {}", dto)
    prisonerMergeService.handlePrisonerMerge(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_PRISONER_MERGE_BATCH_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Endpoint to handle multiple prisoner merge events.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner merges have completed successfully",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to notify VSiP of prisoner merges.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to notify VSiP of prisoner merges.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun notifyVSiPOfPrisonerMerges(
    @RequestBody @Valid
    dto: PrisonerMergeNotificationsDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPOfPrisonerMerges {}", dto)
    prisonerMergeBulkService.handlePrisonerMerges(dto)
    return ResponseEntity(HttpStatus.OK)
  }
}
