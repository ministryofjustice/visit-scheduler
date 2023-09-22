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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService

const val VISIT_NOTIFICATION_CONTROLLER_PATH: String = "/visits/notification"
const val VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/non-association/changed"
const val VISIT_NOTIFICATION_PERSON_RESTRICTION_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/person/restriction/changed"
const val VISIT_NOTIFICATION_PRISONER_RECEIVED_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/prisoner/received"
const val VISIT_NOTIFICATION_PRISONER_RELEASED_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/prisoner/released"
const val VISIT_NOTIFICATION_PRISONER_RESTRICTION_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/prisoner/restriction/changed"
const val VISIT_NOTIFICATION_VISITOR_RESTRICTION_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/visitor/restriction/changed"
@RestController
@Validated
@Tag(name = "Visit notification controller $VISIT_NOTIFICATION_CONTROLLER_PATH")
@RequestMapping(name = "Visit notification Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitNotificationController(
  private val visitNotificationEventService: VisitNotificationEventService,
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
    visitNotificationEventService.handleNonAssociations(nonAssociationChangedNotificationDto)
    return ResponseEntity(HttpStatus.OK)
  }


  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_PERSON_RESTRICTION_CHANGE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a change to person/visitor restriction has taken place",
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
  fun notifyVSiPThatPersonRestrictionChanged(
    @RequestBody @Valid
    dto: PersonRestrictionChangeNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatPersonRestrictionChanged {}", dto)
    visitNotificationEventService.handlePersonRestrictionChangeNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_PRISONER_RECEIVED_CHANGE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a prisoner has been received",
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
  fun notifyVSiPThatPrisonerReceivedChanged(
    @RequestBody @Valid
    dto: PrisonerReceivedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatPrisonerReceivedChanged {}", dto)
    visitNotificationEventService.handlePrisonerReceivedNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_PRISONER_RELEASED_CHANGE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a prisoner has been released",
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
  fun notifyVSiPThatPrisonerReleasedChanged(
    @RequestBody @Valid
    dto: PrisonerReleasedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatPrisonerReleasedChanged {}", dto)
    visitNotificationEventService.handlePrisonerReleasedNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_PRISONER_RESTRICTION_CHANGE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a change to prisoner restriction has taken place",
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
  fun notifyVSiPThatPrisonerRestrictionChanged(
    @RequestBody @Valid
    dto: PrisonerRestrictionChangeNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatPrisonerRestrictionChanged {}", dto)
    visitNotificationEventService.handlePrisonerRestrictionChangeNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_VISITOR_RESTRICTION_CHANGE_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a change to a visitor restriction has taken place",
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
  fun notifyVSiPThatVisitorRestrictionChanged(
    @RequestBody @Valid
    dto: VisitorRestrictionChangeNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatVisitorRestrictionChanged {}", dto)
    visitNotificationEventService.handleVisitorRestrictionChangeNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }
}
