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
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.ENHANCED
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
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationColumnNames.NAME
import java.io.File
import java.io.FileReader
import java.io.PrintWriter
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
    PRISON, ROOM, TYPE, OPEN, CLOSED, ENHANCED, START_TIME, END_TIME, START_DATE, END_DATE, DAY_OF_WEEK, BI_WEEKLY, LOCATION_KEYS;
  }

  private enum class SessionLocationColumnNames {
    PRISON, KEY, LEVEL_ONE, LEVEL_TWO, LEVEL_THREE, LEVEL_FOUR, NAME;
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
    val prisonCode: String,
    val room: String,
    val type: VisitType,
    val open: Int,
    val closed: Int,
    val enhanced: Boolean = false,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val dayOfWeek: DayOfWeek,
    val locationKeys: String?,
    val biWeekly: Boolean = false
  ) {
    constructor(sessionRecord: CSVRecord) : this(
      prisonCode = sessionRecord.get(SessionColumnNames.PRISON.name).uppercase(),
      room = sessionRecord.get(ROOM.name),
      type = VisitType.valueOf(sessionRecord.get(TYPE.name).uppercase()),
      open = Integer.parseInt(sessionRecord.get(OPEN.name)),
      closed = Integer.parseInt(sessionRecord.get(CLOSED.name)),
      enhanced = sessionRecord.get(ENHANCED.name)?.uppercase().toBoolean(),
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

  data class LocationGroupsColumns(
    val prisonCode: String,
    val key: String,
    val levelOne: List<String> = listOf<String>(),
    val levelTwo: List<String> = listOf<String>(),
    val levelThree: List<String> = listOf<String>(),
    val levelFour: List<String> = listOf<String>(),
    val name: String? = null
  ) {
    constructor(sessionRecord: CSVRecord) : this(
      prisonCode = sessionRecord.get(SessionLocationColumnNames.PRISON.name).uppercase(),
      key = sessionRecord.get(KEY.name).uppercase(),
      levelOne = toList(sessionRecord.get(LEVEL_ONE.name)),
      levelTwo = toList(sessionRecord.get(LEVEL_TWO.name)),
      levelThree = toList(sessionRecord.get(LEVEL_THREE.name)),
      levelFour = toList(sessionRecord.get(LEVEL_FOUR.name)),
      name = sessionRecord.get(NAME.name)
    )
  }

  fun validateSessionLocation(locationColumns: List<LocationGroupsColumns>) {

    val childHasMoreThanOneParent = BiPredicate<List<String>, List<String>> { parentLevel, childlevel ->
      parentLevel.size> 1 && childlevel.isNotEmpty()
    }

    val childCantHaveEmptyParent = BiPredicate<List<String>, List<String>> { parentLevel, childlevel ->
      parentLevel.isEmpty() && childlevel.isNotEmpty()
    }

    locationColumns.forEach { sessionLocationColumn ->
      with(sessionLocationColumn) {

        if (levelOne.isEmpty()) {
          throw IllegalArgumentException("Location : must have at least one level one element (prison:$prisonCode key:$key)!")
        }

        if (childHasMoreThanOneParent.test(levelOne, levelTwo) ||
          childHasMoreThanOneParent.test(levelTwo, levelThree) ||
          childHasMoreThanOneParent.test(levelThree, levelFour)
        ) {
          throw IllegalArgumentException("Location : Child can't have more than one parent (prison:$prisonCode key:$key)!")
        }

        if (childCantHaveEmptyParent.test(levelTwo, levelThree) ||
          childCantHaveEmptyParent.test(levelThree, levelFour)
        ) {
          throw IllegalArgumentException("Location : Child can't have empty parent (prison:$prisonCode key:$key)!")
        }
      }
    }
  }

  fun validateSessionTemplate(
    prisonTemplateRecords: List<LocationGroupsColumns>,
    sessionTemplateColumns: List<SessionTemplateColumns>
  ) {

    val levelsByGroups = prisonTemplateRecords.associateBy({ it.key }, { it })

    sessionTemplateColumns.forEach { sessionTemplateColumn ->
      with(sessionTemplateColumn) {
        getLocationList().forEach { locationKey ->
          if (levelsByGroups.containsKey(locationKey)) {
            val sessionLocationColumns = levelsByGroups.get(locationKey)!!
            if (sessionLocationColumns.prisonCode != prisonCode) {
              throw IllegalArgumentException("Session Template : Prison $prisonCode does not match ${sessionLocationColumns.prisonCode} for (prison:$prisonCode key:$locationKeys)!")
            }
          } else {
            throw IllegalArgumentException("Session Template : Location key does not exist $locationKey for (prison:$prisonCode key:$locationKeys)!")
          }
        }
        if (open <0 || closed <0) {
          throw IllegalArgumentException("Session Template : open($open) or close($closed) capacity be cant be less than zero for (prison:$prisonCode key:$locationKeys)!")
        }
        if (open> maxCapacity || closed> maxCapacity) {
          throw IllegalArgumentException("Session Template : open($open) or close($closed) capacity seems a little high for (prison:$prisonCode key:$locationKeys)!")
        }
      }
    }
  }

  data class SessionLocationItem(
    val groupKey: String,
    val levelOne: String,
    val levelTwo: String? = null,
    val levelThree: String? = null,
    val levelFour: String? = null
  )

  data class SessionLocationGroup(
    val key: String,
    val prisonCode: String,
    var name: String,
  )

  fun getSessionRecordsRecords(csvFile: File): List<SessionTemplateColumns> {
    val records: Iterable<CSVRecord> = CVS_FORMAT.parse(FileReader(csvFile))
    val prisonTemplateRecords = ArrayList<SessionTemplateColumns>()
    for (record in records) {
      if (record.size() != SessionColumnNames.values().size) {
        throw IllegalArgumentException("Some session columns are missing line number: ${record.recordNumber}, exspected ${SessionColumnNames.values().size} but got ${record.size()}")
      }
      prisonTemplateRecords.add(SessionTemplateColumns(record))
    }
    return prisonTemplateRecords.toList()
  }

  fun getSessionLocationColumns(csvFile: File): List<LocationGroupsColumns> {
    val reader: Reader = FileReader(csvFile)
    val records: Iterable<CSVRecord> = CVS_FORMAT.parse(reader)
    val prisonTemplateRecords = ArrayList<LocationGroupsColumns>()

    for (record in records) {
      if (record.size() != SessionLocationColumnNames.values().size) {
        throw IllegalArgumentException("Some location columns are missing line number: ${record.recordNumber}, exspected ${SessionLocationColumnNames.values().size} but got ${record.size()}")
      }
      prisonTemplateRecords.add(LocationGroupsColumns(record))
    }

    validateSessionLocation(prisonTemplateRecords)

    return prisonTemplateRecords
  }

  fun getSessionLocationItems(prisonTemplateRecords: List<LocationGroupsColumns>): List<SessionLocationItem> {
    val sessionLocationItems = ArrayList<SessionLocationItem>()
    prisonTemplateRecords.forEach { sessionLocationItems.addAll(createPermittedSessionLocationItems(it)) }

    return sessionLocationItems.toList()
  }

  private fun createPermittedSessionLocationItems(locationGroupsColumns: LocationGroupsColumns): List<SessionLocationItem> {

    val sessionLocationItemList = mutableListOf<SessionLocationItem>()

    with(locationGroupsColumns) {

      val createLevelOne = levelOne.size> 1 || levelTwo.isEmpty()
      val createLevelTwo = levelTwo.size> 1 || levelThree.isEmpty()
      val createLevelThree = levelThree.size> 1 || levelFour.isEmpty()

      if (createLevelOne || createLevelTwo || createLevelThree) {
        locationGroupsColumns.levelOne.forEach { levelOne ->
          if (createLevelOne) {
            sessionLocationItemList.add(SessionLocationItem(groupKey = locationGroupsColumns.key, levelOne = levelOne))
          } else {
            levelTwo.forEach { levelTwo ->
              if (createLevelTwo) {
                sessionLocationItemList.add(SessionLocationItem(groupKey = locationGroupsColumns.key, levelOne = levelOne, levelTwo = levelTwo))
              } else {
                levelThree.forEach { levelThree ->
                  sessionLocationItemList.add(SessionLocationItem(groupKey = locationGroupsColumns.key, levelOne = levelOne, levelTwo = levelTwo, levelThree = levelThree))
                }
              }
            }
          }
        }
      } else {
        levelFour.forEach { levelFour ->
          sessionLocationItemList.add(SessionLocationItem(groupKey = locationGroupsColumns.key, levelOne = levelOne[0], levelTwo = levelTwo[0], levelThree = levelThree[0], levelFour = levelFour))
        }
      }
    }
    return sessionLocationItemList
  }

  fun createSql(
    template: Template,
    sessionRecords: List<SessionTemplateColumns>,
    sessionLocationGroups: List<SessionLocationGroup>,
    sessionLocationItems: List<SessionLocationItem>,
  ): String {
    val input = mutableMapOf<String, Any>()
    input.put("sessionRecords", sessionRecords)
    input.put("groups", sessionLocationGroups)
    input.put("locations", sessionLocationItems)
    input.put("permitted_session_location_index", sessionLocationItems.size + 1)
    input.put("session_template_id_index", sessionRecords.size + 1)
    input.put("session_location_group_id_index", sessionLocationGroups.size + 1)

    val stringWriter = StringWriter()
    template.process(input, stringWriter)
    return stringWriter.toString()
  }

  fun getSessionLocationGroups(locationGroupsColumns: List<LocationGroupsColumns>): List<SessionLocationGroup> {

    val sessionLocationGroups = mutableMapOf<String, SessionLocationGroup>()

    locationGroupsColumns.forEach {

      val sessionLocationGroup = sessionLocationGroups[it.key]
      if (sessionLocationGroup == null) {
        sessionLocationGroups[it.key] = SessionLocationGroup(it.key, it.prisonCode, it.name!!)
      } else if (it.name != null) {
        sessionLocationGroup.name = "${sessionLocationGroup.name}, ${it.name}"
      }
    }
    return ArrayList(sessionLocationGroups.values)
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
  val sessionTemplateColumns = sessionTemplateSQLGenerator.getSessionRecordsRecords(sessionDataFile)
  val locationGroupsColumns = sessionTemplateSQLGenerator.getSessionLocationColumns(sessionLocationDataFile)

  sessionTemplateSQLGenerator.validateSessionTemplate(locationGroupsColumns, sessionTemplateColumns)

  val sessionLocationItems = sessionTemplateSQLGenerator.getSessionLocationItems(locationGroupsColumns)
  val sessionLocationGroups = sessionTemplateSQLGenerator.getSessionLocationGroups(locationGroupsColumns)

  val sql = sessionTemplateSQLGenerator.createSql(template, sessionTemplateColumns, sessionLocationGroups, sessionLocationItems)

  val outputFile = File(path + "R__Session_Template_Data.sql")
  outputFile.delete()
  PrintWriter(outputFile).use { out ->
    out.print(sql)
    print("File created : ${outputFile.absolutePath}!")
  }
}
