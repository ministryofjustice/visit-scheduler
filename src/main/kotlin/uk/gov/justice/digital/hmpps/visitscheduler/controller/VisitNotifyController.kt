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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCallbackNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCreateNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotifyService

const val VISIT_NOTIFY_CONTROLLER_PATH: String = "/visits/notify"
const val VISIT_NOTIFY_CONTROLLER_CREATE_PATH: String = "$VISIT_NOTIFY_CONTROLLER_PATH/create"
const val VISIT_NOTIFY_CONTROLLER_CALLBACK_PATH: String = "$VISIT_NOTIFY_CONTROLLER_PATH/callback"

@RestController
@Validated
@Tag(name = "Visit GOV.UK notify - delivery status controller $VISIT_NOTIFY_CONTROLLER_PATH")
@RequestMapping(name = "Visit GOV.UK notify Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitNotifyController(
  private val visitNotifyService: VisitNotifyService,
) {
  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER__VISIT_NOTIFICATION_ALERTS')")
  @PutMapping(VISIT_NOTIFY_CONTROLLER_CREATE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a message / email has been sent to GOV.UK notify",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "create message added successfully",
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
  fun notifyMessageCreated(
    @RequestBody
    @Valid
    notifyCreateNotificationDto: NotifyCreateNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered createNotifyMessageSent {}", notifyCreateNotificationDto)
    visitNotifyService.handleCreateNotifyEvent(notifyCreateNotificationDto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER__VISIT_NOTIFICATION_ALERTS')")
  @PutMapping(VISIT_NOTIFY_CONTROLLER_CALLBACK_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a callback response has been received from GOV.UK notify",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "callback handled successfully",
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
  fun handleNotifyCallback(
    @RequestBody
    @Valid
    notifyCallbackNotification: NotifyCallbackNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered handleNotifyCallback - {}", notifyCallbackNotification)
    visitNotifyService.handleCallbackNotifyEvent(notifyCallbackNotification)
    return ResponseEntity(HttpStatus.OK)
  }
}
