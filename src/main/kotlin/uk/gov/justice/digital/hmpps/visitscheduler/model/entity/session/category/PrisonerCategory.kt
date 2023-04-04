package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category

/*
TODO - this needs to be reconsidered based on tests and further analysis and might need to be replaced with a String.
 */
enum class PrisonerCategory(
  val code: String,
) {
  A_EXCEPTIONAL("A Exceptional"),
  A_HIGH("A High"),
  A_PROVISIONAL("A Provisional"),
  A_STANDARD("A Standard"),
  B("B"),
  C("C"),
  D("D"),
  YOI_CLOSED("YOI Closed"),
  YOI_OPEN("YOI Open"),
  YOI_RESTRICTED("YOI Restricted"),
  UNSENTENCED("Unsentenced"),
  UNCATEGORISED_SENTENCED_MALE("Uncategorised Sentenced Male"),
  FEMALE_RESTRICTED("Female Restricted"),
  FEMALE_CLOSED("Female Closed"),
  FEMALE_SEMI("Female Semi"),
  FEMALE_OPEN("Female Open"),
}
