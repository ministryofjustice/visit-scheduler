package uk.gov.justice.digital.hmpps.visitscheduler.utils

import freemarker.template.Configuration
import freemarker.template.Configuration.VERSION_2_3_0
import freemarker.template.Template
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.BI_WEEKLY
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.DAY_OF_WEEK
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.END_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.END_TIME
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.LOCATION_KEYS
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.ROOM
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.START_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.START_TIME
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.TYPE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationColumnNames.KEY
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationColumnNames.LEVEL_FOUR
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationColumnNames.LEVEL_ONE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationColumnNames.LEVEL_THREE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationColumnNames.LEVEL_TWO
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.io.StringWriter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.function.BiPredicate
import java.util.stream.Collectors

private const val maxCapacity = 200

class SessionTemplateSQLGenerator {

  private enum class SessionColumnNames {
    PRISON, ROOM, TYPE, OPEN, CLOSED, START_TIME, END_TIME, START_DATE, END_DATE, DAY_OF_WEEK, BI_WEEKLY, LOCATION_KEYS;
  }

  private enum class SessionLocationColumnNames {
    PRISON, KEY, LEVEL_ONE, LEVEL_TWO, LEVEL_THREE, LEVEL_FOUR;
  }

  companion object {
    private const val DELIMITER = ":"
    val CVS_FORMAT: CSVFormat = CSVFormat.DEFAULT.builder()
      .setNullString("")
      .setIgnoreEmptyLines(true)
      .setTrim(true)
      .setCommentMarker('%')
      .setSkipHeaderRecord(true)
      .setHeader()
      .setIgnoreHeaderCase(true)
      .build()

    fun toList(value: String? = null): List<String> {
      val values = value?.uppercase()?.split(DELIMITER)?.toSet() ?: run { listOf<String>() }
      return values.stream().map(String::trim).collect(Collectors.toList())
    }
  }

  data class SessionTemplateColumns(
    val prison: String,
    val room: String,
    val type: VisitType,
    val open: Int,
    val closed: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val dayOfWeek: DayOfWeek,
    val locationKeys: String?,
    val biWeekly: Boolean = false
  ) {
    constructor(sessionRecord: CSVRecord) : this(
      prison = sessionRecord.get(SessionColumnNames.PRISON.name).uppercase(),
      room = sessionRecord.get(ROOM.name),
      type = VisitType.valueOf(sessionRecord.get(TYPE.name).uppercase()),
      open = Integer.parseInt(sessionRecord.get(OPEN.name)),
      closed = Integer.parseInt(sessionRecord.get(CLOSED.name)),
      startTime = LocalTime.parse(sessionRecord.get(START_TIME.name)),
      endTime = LocalTime.parse(sessionRecord.get(END_TIME.name)),
      startDate = LocalDate.parse(sessionRecord.get(START_DATE.name)),
      endDate = sessionRecord.get(END_DATE.name)?.let { LocalDate.parse(it) },
      dayOfWeek = DayOfWeek.valueOf(sessionRecord.get(DAY_OF_WEEK.name).uppercase()),
      locationKeys = sessionRecord.get(LOCATION_KEYS.name)?.uppercase(),
      biWeekly = sessionRecord.get(BI_WEEKLY.name)?.uppercase().toBoolean()
    )

    fun getLocationList(): List<String> {
      return toList(locationKeys)
    }
  }

  data class SessionLocationColumns(
    val prison: String,
    val key: String,
    val levelOne: List<String> = listOf<String>(),
    val levelTwo: List<String> = listOf<String>(),
    val levelThree: List<String> = listOf<String>(),
    val levelFour: List<String> = listOf<String>(),
  ) {
    constructor(sessionRecord: CSVRecord) : this(
      prison = sessionRecord.get(SessionLocationColumnNames.PRISON.name).uppercase(),
      key = sessionRecord.get(KEY.name).uppercase(),
      levelOne = toList(sessionRecord.get(LEVEL_ONE.name)),
      levelTwo = toList(sessionRecord.get(LEVEL_TWO.name)),
      levelThree = toList(sessionRecord.get(LEVEL_THREE.name)),
      levelFour = toList(sessionRecord.get(LEVEL_FOUR.name))
    )
  }

  fun validateSessionLocation(sessionLocationColumns: List<SessionLocationColumns>) {

    val childHasMoreThanOneParent = BiPredicate<List<String>, List<String>> { parentLevel, childlevel ->
      parentLevel.size> 1 && childlevel.isNotEmpty()
    }

    val childCantHaveEmptyParent = BiPredicate<List<String>, List<String>> { parentLevel, childlevel ->
      parentLevel.isEmpty() && childlevel.isNotEmpty()
    }

    sessionLocationColumns.forEach { sessionLocationColumn ->
      with(sessionLocationColumn) {

        if (levelOne.isEmpty()) {
          throw IllegalArgumentException("Location : must have at least one level one element (prison:$prison key:$key)!")
        }

        if (childHasMoreThanOneParent.test(levelOne, levelTwo) ||
          childHasMoreThanOneParent.test(levelTwo, levelThree) ||
          childHasMoreThanOneParent.test(levelThree, levelFour)
        ) {
          throw IllegalArgumentException("Location : Child can't have more than one parent (prison:$prison key:$key)!")
        }

        if (childCantHaveEmptyParent.test(levelTwo, levelThree) ||
          childCantHaveEmptyParent.test(levelThree, levelFour)
        ) {
          throw IllegalArgumentException("Location : Child can't have empty parent (prison:$prison key:$key)!")
        }
      }
    }
  }

  fun validateSessionTemplate(sessionLocationItems: List<SessionLocationItem>, sessionTemplateColumns: List<SessionTemplateColumns>) {

    val levelsByGroups = sessionLocationItems.associateBy({ it.key }, { it })

    sessionTemplateColumns.forEach { sessionTemplateColumn ->
      with(sessionTemplateColumn) {
        getLocationList().forEach { locationKey ->
          if (levelsByGroups.containsKey(locationKey)) {
            val sessionLocationColumns = levelsByGroups.get(locationKey)!!
            if (sessionLocationColumns.prison != prison) {
              throw IllegalArgumentException("Session Template : Prison $prison does not match ${sessionLocationColumns.prison} for (prison:$prison key:$locationKeys)!")
            }
          } else {
            throw IllegalArgumentException("Session Template : Location key does not exist $locationKey for (prison:$prison key:$locationKeys)!")
          }
        }
        if (open <0 || closed <0) {
          throw IllegalArgumentException("Session Template : open($open) or close($closed) capacity be cant be less than zero for (prison:$prison key:$locationKeys)!")
        }
        if (open> maxCapacity || closed> maxCapacity) {
          throw IllegalArgumentException("Session Template : open($open) or close($closed) capacity seems a little high for (prison:$prison key:$locationKeys)!")
        }
      }
    }
  }

  data class SessionLocationItem(
    val prison: String,
    val key: String,
    val levelOne: String,
    val levelTwo: String? = null,
    val levelThree: String? = null,
    val levelFour: String? = null
  )

  fun getSessionRecordsRecords(csvFile: File): List<SessionTemplateColumns> {
    val records: Iterable<CSVRecord> = CVS_FORMAT.parse(FileReader(csvFile))
    val prisonTemplateRecords = ArrayList<SessionTemplateColumns>()
    for (record in records) {
      prisonTemplateRecords.add(SessionTemplateColumns(record))
    }
    return prisonTemplateRecords.toList()
  }

  fun getSessionLocationItems(csvFile: File): List<SessionLocationItem> {
    val reader: Reader = FileReader(csvFile)
    val records: Iterable<CSVRecord> = CVS_FORMAT.parse(reader)
    val prisonTemplateRecords = ArrayList<SessionLocationColumns>()

    for (record in records) {
      prisonTemplateRecords.add(SessionLocationColumns(record))
    }

    validateSessionLocation(prisonTemplateRecords)

    val sessionLocationItems = ArrayList<SessionLocationItem>()
    prisonTemplateRecords.forEach { sessionLocationItems.addAll(createPermittedSessionLocationItems(it)) }

    return sessionLocationItems.toList()
  }

  private fun createPermittedSessionLocationItems(sessionLocationColumns: SessionLocationColumns): List<SessionLocationItem> {

    val sessionLocationItemList = mutableListOf<SessionLocationItem>()

    with(sessionLocationColumns) {

      val createLevelOne = levelOne.size> 1 || levelTwo.isEmpty()
      val createLevelTwo = levelTwo.size> 1 || levelThree.isEmpty()
      val createLevelThree = levelThree.size> 1 || levelFour.isEmpty()

      if (createLevelOne || createLevelTwo || createLevelThree) {
        sessionLocationColumns.levelOne.forEach { levelOne ->
          if (createLevelOne) {
            sessionLocationItemList.add(SessionLocationItem(prison = sessionLocationColumns.prison, key = sessionLocationColumns.key, levelOne = levelOne))
          } else {
            levelTwo.forEach { levelTwo ->
              if (createLevelTwo) {
                sessionLocationItemList.add(SessionLocationItem(prison = sessionLocationColumns.prison, key = sessionLocationColumns.key, levelOne = levelOne, levelTwo = levelTwo))
              } else {
                levelThree.forEach { levelThree ->
                  sessionLocationItemList.add(SessionLocationItem(prison = sessionLocationColumns.prison, key = sessionLocationColumns.key, levelOne = levelOne, levelTwo = levelTwo, levelThree = levelThree))
                }
              }
            }
          }
        }
      } else {
        levelFour.forEach { levelFour ->
          sessionLocationItemList.add(SessionLocationItem(prison = sessionLocationColumns.prison, key = sessionLocationColumns.key, levelOne = levelOne[0], levelTwo = levelTwo[0], levelThree = levelThree[0], levelFour = levelFour))
        }
      }
    }
    return sessionLocationItemList
  }

  fun createSql(
    template: Template,
    sessionRecords: List<SessionTemplateColumns>,
    sessionLocationItems: List<SessionLocationItem>,
  ): String {
    val input = mutableMapOf<String, Any>()
    input.put("sessionRecords", sessionRecords)
    input.put("locations", sessionLocationItems)

    val stringWriter = StringWriter()
    template.process(input, stringWriter)
    return stringWriter.toString()
  }
}

fun main() {
  val path = "src/main/resources/session-template-data/"
  val sessionDataFile = File(path, "session-data.csv")
  val sessionLocationDataFile = File(path, "session-location-data.csv")

  val cfg = Configuration(VERSION_2_3_0)
  cfg.setDirectoryForTemplateLoading(File(path))
  val template = cfg.getTemplate("template.ftl")

  val sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()

  val sessionLocationItems = sessionTemplateSQLGenerator.getSessionLocationItems(sessionLocationDataFile)
  val sessionRecords = sessionTemplateSQLGenerator.getSessionRecordsRecords(sessionDataFile)

  sessionTemplateSQLGenerator.validateSessionTemplate(sessionLocationItems, sessionRecords)

  val sql = sessionTemplateSQLGenerator.createSql(template, sessionRecords, sessionLocationItems)
  print(sql)
}
