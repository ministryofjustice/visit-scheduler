package uk.gov.justice.digital.hmpps.visitscheduler.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonConfigService
import java.time.LocalDate

const val PRISONS_PATH: String = "/prisons"
const val PRISON_EXCLUDE_DATE_PATH: String = "$PRISONS_PATH/prison/{prisonCode}/exclude-date"

// TODO - remove STAFF from var name once we take ADMIN API endpoints to add / remove exclude dates out
const val STAFF_ADD_PRISON_EXCLUDE_DATE: String = "$PRISON_EXCLUDE_DATE_PATH/add"

// TODO - remove STAFF from var name once we take ADMIN API endpoints to add / remove exclude dates out
const val STAFF_REMOVE_PRISON_EXCLUDE_DATE: String = "$PRISON_EXCLUDE_DATE_PATH/remove"
const val GET_PRISON_EXCLUDE_DATES: String = PRISON_EXCLUDE_DATE_PATH

@RestController
@Validated
@Tag(name = "Prison exclude dates rest controller")
@RequestMapping(name = "Prison Exclude Dates Configuration Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonExcludeDatesController(
  private val prisonConfigService: PrisonConfigService,
) {
  // TODO - change return to list of exclude dates or void?
  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(STAFF_ADD_PRISON_EXCLUDE_DATE)
  @Operation(
    summary = "Add exclude date to a prison.",
    description = "Add exclude date to a prison.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "successfully added exclude date to a prison",
      ),
      ApiResponse(
        responseCode = "400",
        description = "exclude date  provided already exists for prison or prison can't be found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to add exclude dates to a prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun addPrisonExcludeDate(
    @Schema(description = "prison id", example = "BHI", required = true)
    @PathVariable
    prisonCode: String,
    @RequestBody
    @Valid
    prisonExcludeDateDto: PrisonExcludeDateDto,
  ): Set<LocalDate> {
    prisonConfigService.addExcludeDate(
      prisonCode,
      prisonExcludeDateDto,
    )
    return prisonConfigService.getPrisonExcludeDates(prisonCode).map { it.excludeDate }.toSet()
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @PutMapping(STAFF_REMOVE_PRISON_EXCLUDE_DATE)
  @Operation(
    summary = "Remove exclude date from a prison.",
    description = "Remove exclude date from a prison.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "successfully removed exclude date from a prison",
      ),
      ApiResponse(
        responseCode = "400",
        description = "exclude date does not exist for prison or prison can't be found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to add exclude dates to a prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun removePrisonExcludeDate(
    @Schema(description = "prison id", example = "BHI", required = true)
    @PathVariable
    prisonCode: String,
    @RequestBody @Valid
    prisonExcludeDateDto: PrisonExcludeDateDto,
  ): Set<LocalDate> {
    prisonConfigService.removeExcludeDate(prisonCode, prisonExcludeDateDto)
    return prisonConfigService.getPrisonExcludeDates(prisonCode).map { it.excludeDate }.toSet()
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER')")
  @GetMapping(GET_PRISON_EXCLUDE_DATES)
  @Operation(
    summary = "Get exclude dates for a prison.",
    description = "Get exclude dates for a prison.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "prison's exclude dates returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonExcludeDates(
    @Schema(description = "prison id", example = "BHI", required = true)
    @PathVariable
    prisonCode: String,
  ): List<PrisonExcludeDateDto> {
    return prisonConfigService.getPrisonExcludeDates(prisonCode)
  }
}
