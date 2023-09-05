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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationService

const val VISIT_NOTIFICATION_CONTROLLER_PATH: String = "/visits/notification"
const val VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/non-association/changed"

@RestController
@Validated
@Tag(name = "Visit notification controller $VISIT_NOTIFICATION_CONTROLLER_PATH")
@RequestMapping(name = "Visit notification Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitNotificationController(
  private val visitNotificationService: VisitNotificationService,
) {
  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that non association between two prisoners has changed",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "notification has completed successfully",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to notify VSiP of change",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to notify VSiP of change",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun notifyVSiPThatNonAssociationHasChanged(
    @RequestBody @Valid
    nonAssociationChangedNotificationDto: NonAssociationChangedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPOfNonAssociationHasChanged {}", nonAssociationChangedNotificationDto)
    visitNotificationService.handleNonAssociations(nonAssociationChangedNotificationDto)
    return ResponseEntity(HttpStatus.OK)
  }
}
