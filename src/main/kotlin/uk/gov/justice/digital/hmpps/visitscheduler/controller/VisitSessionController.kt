package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AvailableVisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.DateRange
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import java.time.LocalDate
import java.time.LocalTime

const val VISIT_SESSION_CONTROLLER_PATH: String = "/visit-sessions"
const val GET_VISIT_SESSION: String = "$VISIT_SESSION_CONTROLLER_PATH/session"
const val VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH: String = "$VISIT_SESSION_CONTROLLER_PATH/available"
const val GET_SESSION_SCHEDULE: String = "$VISIT_SESSION_CONTROLLER_PATH/schedule"
const val GET_SESSION_CAPACITY: String = "$VISIT_SESSION_CONTROLLER_PATH/capacity"

@RestController
@Validated
@RequestMapping(name = "Session Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "2. Visit session rest controller")
class VisitSessionController(
  private val sessionService: SessionService,
) {

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(VISIT_SESSION_CONTROLLER_PATH)
  @Operation(
    summary = "Returns all visit sessions which are within the reservable time period - whether or not they are full",
    description = "Retrieve all visits for a specified prisoner",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit session information returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visit sessions ",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitBookingSessions(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Query by NOMIS Prison Identifier",
      example = "MDI",
    )
    prisonCode: String,
    @RequestParam(value = "prisonerId", required = true)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC",
    )
    prisonerId: String,
    @RequestParam(value = "min", required = false)
    @Parameter(
      description = "Override the default minimum number of days notice from the current date",
      example = "2",
    )
    min: Int?,
    @RequestParam(value = "max", required = false)
    @Parameter(
      description = "Override the default maximum number of days to book-ahead from the current date",
      example = "28",
    )
    max: Int?,
    @Parameter(
      description = "Username for the user making the request. Used to exclude user's pending applications from session capacity count. Optional, ignored if not passed in.",
      example = "user-1",
    )
    username: String? = null,
    @RequestParam
    @Parameter(description = "userType", example = "STAFF", required = true)
    userType: UserType,
  ): List<VisitSessionDto> = sessionService.getAllVisitSessions(prisonCode, prisonerId, minOverride = min, maxOverride = max, usernameToExcludeFromReservedApplications = username, userType = userType)

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH)
  @Operation(
    summary = "Returns only available visit sessions for a specified prisoner by restriction and within the reservable time period",
    description = "Returns only available visit sessions for a specified prisoner by restriction and within the reservable time period",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Available visit session details returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to GET available visit sessions",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getAvailableVisitBookingSessions(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Query by NOMIS Prison Identifier",
      example = "MDI",
    )
    prisonCode: String,
    @RequestParam(value = "prisonerId", required = true)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC",
    )
    prisonerId: String,
    @RequestParam(value = "sessionRestriction", required = true)
    @Parameter(
      description = "Filter results by session restriction - OPEN or CLOSED",
      example = "CLOSED",
    )
    sessionRestriction: SessionRestriction,
    @RequestParam(value = "fromDate", required = true)
    @Parameter(
      description = "Session slot from date",
      example = "2024-12-03",
    )
    fromDate: LocalDate,
    @RequestParam(value = "toDate", required = true)
    @Parameter(
      description = "Session slot to date",
      example = "2024-12-20",
    )
    toDate: LocalDate,
    @RequestParam(value = "excludedApplicationReference", required = false)
    @Parameter(
      description = "The current application reference to be excluded from capacity count and double booking",
      example = "dfs-wjs-eqr",
    )
    excludedApplicationReference: String? = null,
    @RequestParam(value = "username", required = false)
    @Parameter(
      description = "Username for the user making the request. Used to exclude user's pending applications from session capacity count. Optional, ignored if not passed in.",
      example = "user-1",
    )
    username: String? = null,
    @RequestParam
    @Parameter(description = "userType", example = "STAFF", required = true)
    userType: UserType,
  ): List<AvailableVisitSessionDto> = sessionService.getOnlyAvailableVisitSessions(prisonCode, prisonerId, sessionRestriction, DateRange(fromDate, toDate), excludedApplicationReference, usernameToExcludeFromReservedApplications = username, userType = userType)

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_SESSION_SCHEDULE)
  @Operation(
    summary = "Returns session scheduled for given prison and date",
    description = "Returns session scheduled for given prison and date",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Session scheduled information returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get session scheduled",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSessionSchedule(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Query by NOMIS Prison Identifier",
      example = "MDI",
    )
    prisonCode: String,
    @RequestParam(value = "date", required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Parameter(
      description = "Query by session scheduled date",
      example = "2020-11-01",
    )
    scheduleDate: LocalDate,
  ): List<SessionScheduleDto> = sessionService.getSessionSchedule(prisonCode, scheduleDate)

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_SESSION_CAPACITY)
  @Operation(
    summary = "Returns the session capacity for the given sessions",
    description = "Returns the session capacity for the given sessions",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "the session capacity for the given sessions",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request ",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Capacity not found ",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSessionCapacity(
    @RequestParam(value = "prisonId", required = true)
    @Parameter(
      description = "Query by NOMIS Prison Identifier",
      example = "CLI",
    )
    prisonCode: String,
    @RequestParam(value = "sessionDate", required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Parameter(
      description = "Session date",
      example = "2020-11-01",
    )
    sessionDate: LocalDate,
    @RequestParam(value = "sessionStartTime", required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Parameter(
      description = "Session start time",
      example = "13:30:00",
    )
    sessionStartTime: LocalTime,
    @RequestParam(value = "sessionEndTime", required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Parameter(
      description = "Session end time",
      example = "14:30:00",
    )
    sessionEndTime: LocalTime,
  ): SessionCapacityDto = sessionService.getSessionCapacity(prisonCode, sessionDate, sessionStartTime, sessionEndTime)

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_VISIT_SESSION)
  @Operation(
    summary = "Returns a single VSIP session",
    description = "Returns a single VSIP session",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "the session was found and returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Capacity not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getVisitSession(
    @RequestParam(value = "prisonCode", required = true)
    @Parameter(
      description = "Prison code",
      example = "MDI",
    )
    prisonCode: String,
    @RequestParam(value = "sessionDate", required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Parameter(
      description = "Session date",
      example = "2020-11-01",
    )
    sessionDate: LocalDate,
    @RequestParam(value = "sessionTemplateReference", required = true)
    @Parameter(
      description = "Session template reference",
      example = "xye-fjc-abc",
    )
    sessionTemplateReference: String,
  ): VisitSessionDto? = sessionService.getIndividualVisitSession(prisonCode, sessionDate, sessionTemplateReference)
}
