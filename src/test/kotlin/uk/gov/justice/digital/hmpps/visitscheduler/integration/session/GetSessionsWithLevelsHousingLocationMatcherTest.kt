package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Runs the same tests as GetSessionsWithLevelsFullStatusMatcherTest but will call the prison API housing location endpoint.
 */
@DisplayName("Get /visit-sessions with call to prison API housing location")
@TestPropertySource(properties = ["prison.api.levels-endpoint=HOUSING_LOCATION"])
class GetSessionsWithLevelsHousingLocationMatcherTest(@Autowired private val objectMapper: ObjectMapper) : GetSessionsWithLevelsFullStatusMatcherTest(objectMapper)
