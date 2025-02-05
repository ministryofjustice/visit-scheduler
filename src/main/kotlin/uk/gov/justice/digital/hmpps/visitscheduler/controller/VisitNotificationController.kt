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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationCountDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerAlertCreatedUpdatedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorApprovedUnapprovedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService

const val VISIT_NOTIFICATION_CONTROLLER_PATH: String = "/visits/notification"
const val VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/non-association/changed"
const val VISIT_NOTIFICATION_PRISONER_RECEIVED_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/prisoner/received"
const val VISIT_NOTIFICATION_PRISONER_RELEASED_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/prisoner/released"
const val VISIT_NOTIFICATION_PRISONER_RESTRICTION_CHANGE_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/prisoner/restriction/changed"
const val VISIT_NOTIFICATION_PRISONER_ALERTS_UPDATED_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/prisoner/alerts/updated"
const val VISIT_NOTIFICATION_PERSON_RESTRICTION_UPSERTED_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/person/restriction/upserted"
const val VISIT_NOTIFICATION_VISITOR_RESTRICTION_UPSERTED_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/visitor/restriction/upserted"
const val VISIT_NOTIFICATION_VISITOR_APPROVED_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/visitor/approved"
const val VISIT_NOTIFICATION_VISITOR_UNAPPROVED_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/visitor/unapproved"
const val VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/{prisonCode}/count"
const val FUTURE_NOTIFICATION_VISIT_GROUPS: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/{prisonCode}/groups"
const val VISIT_NOTIFICATION_TYPES: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/visit/{reference}/types"
const val VISIT_NOTIFICATION_IGNORE: String = "$VISIT_NOTIFICATION_CONTROLLER_PATH/visit/{reference}/ignore"

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
  @PostMapping(VISIT_NOTIFICATION_PERSON_RESTRICTION_UPSERTED_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that an upsert for a person/visitor restriction has taken place",
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
  fun notifyVSiPThatPersonRestrictionUpserted(
    @RequestBody @Valid
    dto: PersonRestrictionUpsertedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatPersonRestrictionUpserted {}", dto)
    visitNotificationEventService.handlePersonRestrictionUpsertedNotification(dto)
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
  @PostMapping(VISIT_NOTIFICATION_PRISONER_ALERTS_UPDATED_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a prisoner alert has been created or updated",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "notification has completed successfully",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to notify VSiP of alert",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to notify VSiP of alert",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun notifyVSiPThatPrisonerAlertCreatedUpdated(
    @RequestBody @Valid
    dto: PrisonerAlertCreatedUpdatedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatPrisonerAlertCreatedUpdated {}", dto)
    visitNotificationEventService.handlePrisonerAlertCreatedUpdatedNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_VISITOR_RESTRICTION_UPSERTED_PATH)
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
  fun notifyVSiPThatVisitorRestrictionUpserted(
    @RequestBody @Valid
    dto: VisitorRestrictionUpsertedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatVisitorRestrictionUpserted {}", dto)
    visitNotificationEventService.handleVisitorRestrictionUpsertedNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_VISITOR_APPROVED_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a visitor has been approved",
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
  fun notifyVSiPThatVisitorApproved(
    @RequestBody @Valid
    dto: VisitorApprovedUnapprovedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatVisitorApproved {}", dto)
    visitNotificationEventService.handleVisitorApprovedNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PostMapping(VISIT_NOTIFICATION_VISITOR_UNAPPROVED_PATH)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "To notify VSiP that a visitor has been unapproved",
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
  fun notifyVSiPThatVisitorUnapproved(
    @RequestBody @Valid
    dto: VisitorApprovedUnapprovedNotificationDto,
  ): ResponseEntity<HttpStatus> {
    LOG.debug("Entered notifyVSiPThatVisitorUnapproved {}", dto)
    visitNotificationEventService.handleVisitorUnapprovedNotification(dto)
    return ResponseEntity(HttpStatus.OK)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH)
  @Operation(
    summary = "Get notification count for a prison",
    description = "Retrieve notification count by prison code",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieve notification count for a prison",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getNotificationCountForPrison(
    @Schema(description = "prisonCode", example = "CFI", required = true)
    @PathVariable
    prisonCode: String,
    @Schema(description = "list of notificationEventTypes enabled on the front end", required = true)
    @RequestParam(value = "types", required = false)
    notificationEventTypes: List<NotificationEventType>?,
  ): NotificationCountDto {
    return NotificationCountDto(visitNotificationEventService.getNotificationCountForPrison(prisonCode, notificationEventTypes))
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(FUTURE_NOTIFICATION_VISIT_GROUPS)
  @Operation(
    summary = "get future notification visit groups by prison code",
    description = "Retrieve future notification visit groups by prison code",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved future notification visit groups by prison code",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getFutureNotificationVisitGroups(
    @Schema(description = "prisonCode", example = "CFI", required = true)
    @PathVariable
    prisonCode: String,
  ): List<NotificationGroupDto> {
    return visitNotificationEventService.getFutureNotificationVisitGroups(prisonCode)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(VISIT_NOTIFICATION_TYPES)
  @Operation(
    summary = "get visit notification types by booking reference",
    description = "Retrieve visit  notification types by booking reference",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved visit  notification types by booking reference",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getNotificationTypesForBookingReference(
    @Schema(description = "bookingReference", example = "v9*d7*ed*7u", required = true)
    @PathVariable
    reference: String,
  ): List<NotificationEventType> {
    return visitNotificationEventService.getNotificationsTypesForBookingReference(reference)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(VISIT_NOTIFICATION_IGNORE)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Do not change an existing booked visit and ignore all notifications",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = IgnoreVisitNotificationsDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit notifications cleared and reason noted.",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to ignore visit notifications.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to ignore visit notifications.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun ignoreVisitNotifications(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    ignoreNotifications: IgnoreVisitNotificationsDto,
  ): VisitDto {
    return visitNotificationEventService.ignoreVisitNotifications(reference.trim(), ignoreNotifications)
  }
}
