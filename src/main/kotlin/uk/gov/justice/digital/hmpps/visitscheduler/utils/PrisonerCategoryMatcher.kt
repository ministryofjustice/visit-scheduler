package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.util.function.BiPredicate
import java.util.stream.Collectors

@Component
class PrisonerCategoryMatcher : BiPredicate<String?, SessionTemplate> {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun test(
    prisonerCategory: String?,
    sessionTemplate: SessionTemplate,
  ): Boolean = isPrisonerCategoryAllowedOnSession(sessionTemplate, prisonerCategory)

  fun isPrisonerCategoryAllowedOnSession(sessionTemplate: SessionTemplate, prisonerCategory: String?): Boolean {
    prisonerCategory?.let {
      val includeCategory = sessionTemplate.includeCategoryGroupType
      val allowedCategories = getAllowedCategoriesForSessionTemplate(sessionTemplate)
      val match = if (includeCategory) {
        allowedCategories.any { category -> category.equals(prisonerCategory, false) }
      } else {
        allowedCategories.none { category -> category.equals(prisonerCategory, false) }
      }
      LOG.debug("isPrisonerCategoryAllowedOnSession prisonerCategory:$prisonerCategory, matched $match, sessionTemplate:${sessionTemplate.reference}")
      return match
    }

    // if prisoner category is null - return false as prisoner should not be allowed on restricted category sessions
    return false
  }

  private fun getAllowedCategoriesForSessionTemplate(sessionTemplate: SessionTemplate): Set<String> = sessionTemplate.permittedSessionCategoryGroups.stream()
    .flatMap { it.sessionCategories.stream() }
    .map { it.prisonerCategoryType.code }
    .collect(Collectors.toSet())
}
