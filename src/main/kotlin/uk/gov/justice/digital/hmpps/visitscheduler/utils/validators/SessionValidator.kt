package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class SessionValidator(val name: String, val description: String)
