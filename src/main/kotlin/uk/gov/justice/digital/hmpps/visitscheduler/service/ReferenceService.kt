package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder

@Service
class ReferenceService {

  companion object {
    const val REF_DELIMITER_DEFAULT = "-"
    const val REF_LENGTH_DEFAULT = 8
  }

  fun createReference(id: Long): String {
    return QuotableEncoder(delimiter = REF_DELIMITER_DEFAULT, minLength = REF_LENGTH_DEFAULT).encode(id)
  }
}
