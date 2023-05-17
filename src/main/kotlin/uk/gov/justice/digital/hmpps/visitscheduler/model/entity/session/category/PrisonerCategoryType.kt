package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category

enum class PrisonerCategoryType(
  val code: String,
) {
  A_EXCEPTIONAL("E"),
  A_HIGH("H"),
  A_PROVISIONAL("P"),
  A_STANDARD("A"),
  B("B"),
  C("C"),
  D("D"),
  YOI_CLOSED("I"),
  YOI_OPEN("J"),
  YOI_RESTRICTED("V"),
  UNSENTENCED("U"),
  UNCATEGORISED_SENTENCED_MALE("X"),
  FEMALE_RESTRICTED("Q"),
  FEMALE_CLOSED("R"),
  FEMALE_SEMI("S"),
  FEMALE_OPEN("T"),
}
