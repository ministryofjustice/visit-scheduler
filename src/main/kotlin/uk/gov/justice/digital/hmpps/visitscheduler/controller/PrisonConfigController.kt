package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonConfigService

const val PRISONS_CONFIG_PATH: String = "/config/prisons"
const val SUPPORTED_PRISONS: String = "$PRISONS_CONFIG_PATH/supported"
const val PRISON_CONFIG_PATH: String = "$PRISONS_CONFIG_PATH/prison"
const val PRISON: String = "$PRISON_CONFIG_PATH/{prisonCode}"
const val ACTIVATE_PRISON: String = "$PRISON/activate"
const val DEACTIVATE_PRISON: String = "$PRISON/deactivate"

@RestController
@Validated
@Tag(name = "6. Prison admin rest controller")
@RequestMapping(name = "Prison Configuration Resource", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonConfigController(
  private val prisonConfigService: PrisonConfigService,
) {

  @PreAuthorize("hasAnyRole('VISIT_SCHEDULER','VISIT_SCHEDULER_CONFIG')")
  @GetMapping(SUPPORTED_PRISONS)
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
    return prisonConfigService.getSupportedPrisons()
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(PRISON)
  @Operation(
    summary = "Gets prison by given prison id/code",
    description = "Gets prison by given prison id/code",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "prison returned",
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
  fun getPrison(
    @Schema(description = "prison id", example = "BHI", required = true)
    @PathVariable
    prisonCode: String,
  ): PrisonDto {
    return prisonConfigService.getPrison(prisonCode)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @GetMapping(PRISONS_CONFIG_PATH)
  @Operation(
    summary = "Get all prisons",
    description = "Get all prisons",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "prison returned",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = PrisonDto::class)),
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
        description = "Incorrect permissions to get all prisons",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisons(): List<PrisonDto> {
    return prisonConfigService.getPrisons()
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PostMapping(PRISON_CONFIG_PATH)
  @Operation(
    summary = "Create a prison",
    description = "Create a prison",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = PrisonDto::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prison created",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to create prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createPrison(
    @RequestBody @Valid
    prisonDto: PrisonDto,
  ): PrisonDto {
    return prisonConfigService.createPrison(prisonDto)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(ACTIVATE_PRISON)
  @Operation(
    summary = "Activate prison using given prison id/code",
    description = "Activate prison using given prison id/code",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "prison activated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to activate prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "prison cant be found to activate",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun activatePrison(
    @Schema(description = "prison id", example = "BHI", required = true)
    @PathVariable
    prisonCode: String,
  ): PrisonDto {
    return prisonConfigService.activatePrison(prisonCode)
  }

  @PreAuthorize("hasRole('VISIT_SCHEDULER_CONFIG')")
  @PutMapping(DEACTIVATE_PRISON)
  @Operation(
    summary = "Deactivate prison using given prison id/code",
    description = "Deactivate prison using given prison id/code",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "prison deactivated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to deactivate prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "prison cant be found to deactivate",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deActivatePrison(
    @Schema(description = "prison id", example = "BHI", required = true)
    @PathVariable
    prisonCode: String,
  ): PrisonDto {
    return prisonConfigService.deActivatePrison(prisonCode)
  }
}
