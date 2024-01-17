package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonsService

const val PRISONS_PATH: String = "/config/prisons/supported"

@RestController
@Validated
@Tag(name = "4. OldVisit prisons rest controller")
@RequestMapping(name = "OldVisit Prisons Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitPrisonsController(
  private val prisonsService: PrisonsService,
) {

  @PreAuthorize("hasAnyRole('VISIT_SCHEDULER','VISIT_SCHEDULER_CONFIG')")
  @GetMapping(PRISONS_PATH)
  @Operation(
    summary = "Get supported prisons",
    description = "Get all supported prisons id's",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Supported prisons returned",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = String::class)),
            examples = [
              ExampleObject(value = "[\"HEI\", \"MDI\"]"),
            ],
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get supported prisons",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSupportedPrisons(): List<String> {
    return prisonsService.getSupportedPrisons()
  }
}
