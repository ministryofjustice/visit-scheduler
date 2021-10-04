package uk.gov.justice.digital.hmpps.visitscheduler.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitSchedulerService
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank

@RestController
@Validated
@RequestMapping(name = "Visit Resource", path = ["/visits"], produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitResource(
  private val visitSchedulerService: VisitSchedulerService
) {

  @PreAuthorize("hasRole('ROLE_PLACEHOLDER_VISIT')")
  @GetMapping("/prisoner/{prisonerId}")
  @Operation(
    summary = "Get visits for prisoner",
    description = "Retrieve all visits for a specified prisoner",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit Information Returned",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = VisitDto::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to Get visits for prisoner",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getVisits(
    @Schema(description = "Username", example = "AD12345G", required = true)
    @PathVariable prisonerId: String
  ): List<VisitDto> =
    visitSchedulerService.findVisits(prisonerId)

}

@JsonInclude(NON_NULL)
@Schema(description = "Visit")
data class VisitDto(
  @Schema(description = "Visit id", example = "123", required = true) val id: Long,
  @Schema(description = "prisonerId", example = "AF34567G", required = true) val prisonerId: String,
  @Schema(
    description = "The date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotBlank val visitDateTime: LocalDateTime
) {

  constructor(visitEntity: Visit) : this(
    visitEntity.id, visitEntity.prisonerId, visitEntity.visitDateTime
  )

}
