package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionCapacityValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionDateRangeValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionTimeSlotValidation
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionTemplateVisitOrderRestrictionType

data class UpdateSessionTemplateDto(
  @param:Schema(description = "Name for Session template", example = "Monday Xmas", required = true)
  @field:NotBlank
  @field:Size(max = 100)
  val name: String? = null,

  @param:Schema(description = "The start and end time of the generated visit session(s)", required = false)
  @field:SessionTimeSlotValidation
  val sessionTimeSlot: SessionTimeSlotDto?,

  @param:Schema(description = "The start and end date of the Validity period for the session template", required = false)
  @field:SessionDateRangeValidation
  val sessionDateRange: SessionDateRangeDto?,

  @param:Schema(description = "Visit Room", example = "Visits Main Hall", required = false)
  @field:Size(max = 255)
  val visitRoom: String?,

  @param:Schema(description = "The open and closed capacity of the session template", required = false)
  @field:SessionCapacityValidation
  @field:Valid
  val sessionCapacity: SessionCapacityDto?,

  @param:Schema(description = "number of weeks until the weekly day is repeated", example = "1", required = false)
  @field:Min(1)
  val weeklyFrequency: Int?,

  @param:Schema(description = "list of group references for permitted session location groups", required = false)
  val locationGroupReferences: List<String>? = null,

  @param:Schema(description = "list of group references for allowed prisoner category groups", required = false)
  val categoryGroupReferences: List<String>? = null,

  @param:Schema(description = "list of group references for allowed prisoner incentive levels", required = false)
  val incentiveLevelGroupReferences: List<String>? = null,

  @param:Schema(description = "Session template user clients.", required = false)
  val clients: List<UserClientDto>? = null,

  @param:Schema(description = "Determines behaviour of location groups. True equates to these location groups being included, false equates to them being excluded.", required = false)
  val includeLocationGroupType: Boolean? = null,

  @param:Schema(description = "Determines behaviour of category groups. True equates to these category groups being included, false equates to them being excluded.", required = false)
  val includeCategoryGroupType: Boolean? = null,

  @param:Schema(description = "Determines behaviour of incentive groups. True equates to these incentive groups being included, false equates to them being excluded.", required = false)
  val includeIncentiveGroupType: Boolean? = null,

  @param:Schema(description = "The type of visit order restriction, defaults to VO_PVO (Either allowed)", example = "PVO", implementation = SessionTemplateVisitOrderRestrictionType::class, required = false)
  val visitOrderRestriction: SessionTemplateVisitOrderRestrictionType? = null,
)
