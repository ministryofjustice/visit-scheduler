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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.MigrateVisitService

const val MIGRATE_VISITS: String = "/migrate-visits"
const val MIGRATE_CANCEL: String = "$MIGRATE_VISITS/{reference}/cancel"

@RestController
@Validated
@Tag(name = "7. Visit migration rest controller")
@RequestMapping(name = "Visit Migration Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class MigrateController(
  private val migrateVisitService: MigrateVisitService,
) {

  @PreAuthorize("hasAnyRole('MIGRATE_VISITS', 'MIGRATION_ADMIN')")
  @PostMapping(MIGRATE_VISITS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Migrate a visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = MigrateVisitRequestDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit migrated",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to migrate a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to migrate a visit",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun migrateVisit(
    @RequestBody @Valid
    migrateVisitRequest: MigrateVisitRequestDto,
  ): String {
    return migrateVisitService.migrateVisit(migrateVisitRequest)
  }

  @PreAuthorize("hasAnyRole('MIGRATE_VISITS', 'MIGRATION_ADMIN')")
  @PutMapping(MIGRATE_CANCEL)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Migrate a canceled booked visit",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = OutcomeDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Cancelled visit migrated",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to cancelled visit migrated",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to cancelled visit migrated",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun cancelVisit(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable
    reference: String,
    @RequestBody @Valid
    cancelVisitDto: CancelVisitDto,
  ): VisitDto {
    return migrateVisitService.cancelVisit(reference.trim(), cancelVisitDto)
  }
}
