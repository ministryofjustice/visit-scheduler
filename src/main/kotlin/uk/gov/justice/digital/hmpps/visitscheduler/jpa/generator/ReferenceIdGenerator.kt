package uk.gov.justice.digital.hmpps.visitscheduler.jpa.generator

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.internal.util.config.ConfigurationHelper
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.LongType
import org.hibernate.type.Type
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.io.Serializable
import java.util.Properties

@Suppress("unused")
class ReferenceIdGenerator : SequenceStyleGenerator() {

  private var refDelimiter: String = REF_DELIMITER_DEFAULT
  private var refLength: Int = REF_LENGTH_DEFAULT

  override fun configure(type: Type?, params: Properties?, serviceRegistry: ServiceRegistry?) {
    super.configure(LongType.INSTANCE, params, serviceRegistry)
    refDelimiter = ConfigurationHelper.getString(REF_DELIMITER_PARAMETER, params, REF_DELIMITER_DEFAULT)
    refLength = ConfigurationHelper.getInt(REF_LENGTH_PARAMETER, params, REF_LENGTH_DEFAULT)
  }

  override fun generate(session: SharedSessionContractImplementor?, `object`: Any?): Serializable {
    val id = super.generate(session, `object`)
    return QuotableEncoder(delimiter = refDelimiter, minLength = refLength).encode(id as Long)
  }

  companion object {
    const val REF_DELIMITER_PARAMETER = "refDelimiter"
    const val REF_DELIMITER_DEFAULT = "-"
    const val REF_LENGTH_PARAMETER = "refLength"
    const val REF_LENGTH_DEFAULT = 8
  }
}
