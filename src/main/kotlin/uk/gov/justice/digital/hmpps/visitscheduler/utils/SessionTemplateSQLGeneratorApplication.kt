package uk.gov.justice.digital.hmpps.visitscheduler.utils

import freemarker.template.Configuration
import freemarker.template.Configuration.VERSION_2_3_0
import freemarker.template.Template
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.BI_WEEKLY
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.CATEGORY_KEYS
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.DAY_OF_WEEK
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.END_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.END_TIME
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.ENHANCED
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.LOCATION_KEYS
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.START_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.START_TIME
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.TYPE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionColumnNames.VISIT_ROOM
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
    PRISON, VISIT_ROOM, TYPE, OPEN, CLOSED, ENHANCED, START_TIME, END_TIME, START_DATE, END_DATE, DAY_OF_WEEK, BI_WEEKLY, LOCATION_KEYS, CATEGORY_KEYS;
  }

  private enum class SessionLocationColumnNames {
    PRISON, KEY, LEVEL_ONE, LEVEL_TWO, LEVEL_THREE, LEVEL_FOUR, NAME;
  }

  private enum class SessionPrisonerCategoryColumnNames {
    PRISON, KEY, CATEGORY, NAME;
  }

  enum class GroupType(val type: String, val file: String, val columnSize: Int) {
    LOCATION("location", "session-location-data.csv", SessionLocationColumnNames.values().size),
    PRISONER_CATEGORY("prisoner-category", "session-prisoner-category-data.csv", SessionPrisonerCategoryColumnNames.values().size),
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
      val values = value?.uppercase()?.split(DELIMITER)?.toSet() ?: run { listOf() }
      return values.stream().map(String::trim).collect(Collectors.toList())
    }
  }

  private fun validateSessionLocation(sessionLocationColumn: LocationGroupsColumns) {
    val childHasMoreThanOneParent = BiPredicate<List<String>, List<String>> { parentLevel, childlevel ->
      parentLevel.size > 1 && childlevel.isNotEmpty()
    }

    val childCantHaveEmptyParent = BiPredicate<List<String>, List<String>> { parentLevel, childlevel ->
      parentLevel.isEmpty() && childlevel.isNotEmpty()
    }

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

  private fun validateSessionPrisonerCategory(sessionPrisonerCategoryGroupsColumn: PrisonerCategoryGroupsColumns) {
    with(sessionPrisonerCategoryGroupsColumn) {
      if (categoryCodes.isEmpty()) {
        throw IllegalArgumentException("Category : must have at least one category code (prison:$prisonCode key:$key)!")
      }

      categoryCodes.forEach {
        try {
          PrisonerCategoryType.valueOf(it)
        } catch (e: Exception) {
          val allowedValues = PrisonerCategoryType.values().joinToString(",")
          throw IllegalArgumentException("Category : Invalid category code - $it - allowed values are - $allowedValues  (prison:$prisonCode key:$key)!")
        }
      }
    }
  }

  private fun validateSessionTemplate(
    groupType: GroupType,
    groupColumns: List<GroupColumns>,
    sessionTemplateColumns: List<SessionTemplateColumns>,
  ) {
    sessionTemplateColumns.forEach { sessionTemplateColumn ->
      with(sessionTemplateColumn) {
        val keyList = when (groupType) {
          GroupType.LOCATION -> getLocationList()
          GroupType.PRISONER_CATEGORY -> getPrisonerCategoryList()
        }
        validateSessionTemplateGroupColumn(groupType, sessionTemplateColumn, keyList, groupColumns)

        if (open < 0 || closed < 0) {
          throw IllegalArgumentException("Session Template : open($open) or close($closed) capacity be cant be less than zero for (prison:$prisonCode key:$locationKeys)!")
        }
        if (open > maxCapacity || closed > maxCapacity) {
          throw IllegalArgumentException("Session Template : open($open) or close($closed) capacity seems a little high for (prison:$prisonCode key:$locationKeys)!")
        }
      }
    }
  }

  private fun validateSessionTemplateGroupColumn(groupType: GroupType, sessionTemplateColumn: SessionTemplateColumns, keyList: List<String>, groupColumnsList: List<GroupColumns>) {
    val levelsByGroups = groupColumnsList.associateBy({ it.key }, { it })

    keyList.forEach { key ->
      if (levelsByGroups.containsKey(key)) {
        val sessionLocationGroupColumns = levelsByGroups[key]!!
        if (sessionLocationGroupColumns.prisonCode != sessionTemplateColumn.prisonCode) {
          throw IllegalArgumentException("Session Template : Prison ${sessionTemplateColumn.prisonCode} does not match ${sessionTemplateColumn.prisonCode} for (prison:${sessionTemplateColumn.prisonCode} key:${sessionTemplateColumn.locationKeys})!")
        }
      } else {
        throw IllegalArgumentException("Session Template : ${groupType.type} key does not exist $key for (prison:${sessionTemplateColumn.prisonCode} key:${sessionTemplateColumn.locationKeys})!")
      }
    }
  }

  fun getSessionRecords(csvFile: File): List<SessionTemplateColumns> {
    val records: Iterable<CSVRecord> = CVS_FORMAT.parse(FileReader(csvFile))
    val prisonTemplateRecords = ArrayList<SessionTemplateColumns>()
    for (record in records) {
      if (record.size() != SessionColumnNames.values().size) {
        throw IllegalArgumentException("Some session columns are missing line number: ${record.recordNumber}, expected ${SessionColumnNames.values().size} but got ${record.size()} ${record.toList()}")
      }
      prisonTemplateRecords.add(SessionTemplateColumns(record))
    }
    return prisonTemplateRecords.toList()
  }

  private fun getCsvRecords(csvFile: File): Iterable<CSVRecord> {
    val reader: Reader = FileReader(csvFile)
    return CVS_FORMAT.parse(reader)
  }

  fun getSessionGroupColumns(groupType: GroupType, csvFile: File): List<GroupColumns> {
    val records = getCsvRecords(csvFile)
    val groupColumns = ArrayList<GroupColumns>()

    for (record in records) {
      if (record.size() != groupType.columnSize) {
        throw IllegalArgumentException("Some ${groupType.type} columns are missing ${csvFile.name} line number: ${record.recordNumber}, expected ${groupType.columnSize} but got ${record.size()}")
      }

      groupColumns.add(getGroupColumn(groupType, record))
    }
    validateGroupColumns(groupColumns)
    return groupColumns
  }

  private fun getGroupColumn(groupType: GroupType, csvRecord: CSVRecord): GroupColumns {
    return when (groupType) {
      GroupType.LOCATION -> LocationGroupsColumns(csvRecord)
      GroupType.PRISONER_CATEGORY -> PrisonerCategoryGroupsColumns(csvRecord)
    }
  }

  fun validateGroupColumns(groupColumns: List<GroupColumns>) {
    groupColumns.forEach {
      validateGroupColumn(it)
    }
  }
  private fun validateGroupColumn(groupColumn: GroupColumns) {
    when (groupColumn) {
      is LocationGroupsColumns -> validateSessionLocation(groupColumn)
      is PrisonerCategoryGroupsColumns -> validateSessionPrisonerCategory(groupColumn)
    }
  }

  fun getSessionItems(groupColumnsList: List<GroupColumns>): List<SessionItem> {
    val sessionItems = ArrayList<SessionItem>()
    groupColumnsList.forEach {
      when (it) {
        is LocationGroupsColumns -> sessionItems.addAll(createPermittedSessionLocationItems(it))
        is PrisonerCategoryGroupsColumns -> sessionItems.addAll(createPrisonerCategoryItem(it))
      }
    }
    return sessionItems.toList()
  }

  private fun createPermittedSessionLocationItems(locationGroupsColumns: LocationGroupsColumns): List<SessionLocationItem> {
    val sessionLocationItemList = mutableListOf<SessionLocationItem>()

    with(locationGroupsColumns) {
      val createLevelOne = levelOne.size > 1 || levelTwo.isEmpty()
      val createLevelTwo = levelTwo.size > 1 || levelThree.isEmpty()
      val createLevelThree = levelThree.size > 1 || levelFour.isEmpty()

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

  private fun createPrisonerCategoryItem(categoryGroupsColumns: PrisonerCategoryGroupsColumns): List<SessionPrisonerCategoryItem> {
    val sessionLocationItemList = mutableListOf<SessionPrisonerCategoryItem>()
    categoryGroupsColumns.categoryCodes.forEach {
      sessionLocationItemList.add(
        SessionPrisonerCategoryItem(groupKey = categoryGroupsColumns.key, prisonerCategoryType = PrisonerCategoryType.valueOf(it)),
      )
    }

    return sessionLocationItemList
  }

  fun createSql(
    template: Template,
    sessionRecords: List<SessionTemplateColumns>,
    groupItemMap: MutableMap<GroupType, Tuple2<List<SessionGroup>, List<SessionItem>>>,
  ): String {
    val prisonCodes = sessionRecords.associateBy({ it.prisonCode }, { it.prisonCode })
    val sessionLocationGroups = groupItemMap[GroupType.LOCATION]?.t1 ?: ArrayList()
    val sessionLocationItems = groupItemMap[GroupType.LOCATION]?.t2 ?: ArrayList()
    val sessionCategoryGroups = groupItemMap[GroupType.PRISONER_CATEGORY]?.t1 ?: ArrayList()
    val sessionCategoryItems = groupItemMap[GroupType.PRISONER_CATEGORY]?.t2 ?: ArrayList()

    val input = mutableMapOf<String, Any>()
    input["prisonCodes"] = prisonCodes.values
    input["sessionRecords"] = sessionRecords
    input["groups"] = sessionLocationGroups
    input["locations"] = sessionLocationItems
    input["categoryGroups"] = sessionCategoryGroups
    input["categories"] = sessionCategoryItems
    input["permitted_session_location_index"] = sessionLocationItems.size + 1
    input["session_template_id_index"] = sessionRecords.size + 1
    input["session_location_group_id_index"] = sessionLocationGroups.size + 1
    input["session_category_group_id_index"] = sessionCategoryGroups.size + 1
    input["session_prisoner_category_index"] = sessionCategoryItems.size + 1

    val stringWriter = StringWriter()
    template.process(input, stringWriter)
    return stringWriter.toString()
  }
  private fun getSessionGroups(groupsColumns: List<GroupColumns>): List<SessionGroup> {
    val sessionGroups = mutableMapOf<String, SessionGroup>()

    groupsColumns.forEach {
      val sessionLocationGroup = sessionGroups[it.key]
      if (sessionLocationGroup == null) {
        sessionGroups[it.key] = SessionGroup(it.key, it.prisonCode, it.name!!)
      } else if (it.name != null) {
        sessionLocationGroup.name = "${sessionLocationGroup.name}, ${it.name}"
      }
    }
    return ArrayList(sessionGroups.values)
  }

  fun generateGroupValuesMap(path: String, sessionTemplateColumns: List<SessionTemplateColumns>): MutableMap<GroupType, Tuple2<List<SessionGroup>, List<SessionItem>>> {
    val groupValuesMap =
      mutableMapOf<GroupType, Tuple2<List<SessionGroup>, List<SessionItem>>>()

    GroupType.values().forEach { groupType ->
      val groupDataFile = File(path, groupType.file)

      val groupsColumns =
        getSessionGroupColumns(
          groupType,
          groupDataFile,
        )
      validateSessionTemplate(groupType, groupsColumns, sessionTemplateColumns)

      val sessionItems = getSessionItems(groupsColumns)
      val sessionGroups = getSessionGroups(groupsColumns)

      val groupItemTuple = Tuples.of(sessionGroups, sessionItems)
      groupValuesMap[groupType] = groupItemTuple
    }

    return groupValuesMap
  }
  data class SessionTemplateColumns(
    val prisonCode: String,
    val visitRoom: String,
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
    val biWeekly: Boolean = false,
    val categoryKeys: String?,
  ) {
    constructor(sessionRecord: CSVRecord) : this(
      prisonCode = sessionRecord.get(SessionColumnNames.PRISON.name).uppercase(),
      visitRoom = sessionRecord.get(VISIT_ROOM.name),
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
      biWeekly = sessionRecord.get(BI_WEEKLY.name)?.uppercase().toBoolean(),
      categoryKeys = sessionRecord.get(CATEGORY_KEYS.name)?.uppercase(),
    )

    fun getLocationList(): List<String> {
      return toList(locationKeys)
    }

    fun getPrisonerCategoryList(): List<String> {
      return toList(categoryKeys)
    }
  }

  abstract class GroupColumns(
    open var prisonCode: String,
    open val key: String,
    open val name: String? = null,
  )

  data class LocationGroupsColumns(
    override var prisonCode: String,
    override val key: String,
    val levelOne: List<String> = listOf(),
    val levelTwo: List<String> = listOf(),
    val levelThree: List<String> = listOf(),
    val levelFour: List<String> = listOf(),
    override val name: String? = null,
  ) : GroupColumns(prisonCode, key, name) {
    constructor(sessionRecord: CSVRecord) : this(
      prisonCode = sessionRecord.get(SessionLocationColumnNames.PRISON.name).uppercase(),
      key = sessionRecord.get(KEY.name).uppercase(),
      levelOne = toList(sessionRecord.get(LEVEL_ONE.name)),
      levelTwo = toList(sessionRecord.get(LEVEL_TWO.name)),
      levelThree = toList(sessionRecord.get(LEVEL_THREE.name)),
      levelFour = toList(sessionRecord.get(LEVEL_FOUR.name)),
      name = sessionRecord.get(NAME.name),
    )
  }

  data class PrisonerCategoryGroupsColumns(
    override var prisonCode: String,
    override val key: String,
    val categoryCodes: List<String> = listOf(),
    override val name: String? = null,
  ) : GroupColumns(prisonCode, key, name) {
    constructor(sessionRecord: CSVRecord) : this(
      prisonCode = sessionRecord.get(SessionPrisonerCategoryColumnNames.PRISON.name).uppercase(),
      key = sessionRecord.get(SessionPrisonerCategoryColumnNames.KEY.name).uppercase(),
      categoryCodes = toList(sessionRecord.get(SessionPrisonerCategoryColumnNames.CATEGORY.name)),
      name = sessionRecord.get(SessionPrisonerCategoryColumnNames.NAME.name),
    )
  }

  abstract class SessionItem(
    open val groupKey: String,
  )

  data class SessionLocationItem(
    override val groupKey: String,
    val levelOne: String,
    val levelTwo: String? = null,
    val levelThree: String? = null,
    val levelFour: String? = null,
  ) : SessionItem(groupKey)

  data class SessionPrisonerCategoryItem(
    override val groupKey: String,
    val prisonerCategoryType: PrisonerCategoryType,
  ) : SessionItem(groupKey)

  data class SessionGroup(
    val key: String,
    val prisonCode: String,
    var name: String,
  )
}

fun main() {
  val path = "src/main/resources/session-template-data/"
  val sessionDataFile = File(path, "session-data.csv")

  val cfg = Configuration(VERSION_2_3_0)
  cfg.setDirectoryForTemplateLoading(File(path))
  val template = cfg.getTemplate("template.ftl")

  val sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()
  val sessionTemplateColumns = sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)
  val groupValuesMap = sessionTemplateSQLGenerator.generateGroupValuesMap(path, sessionTemplateColumns)

  val sql = sessionTemplateSQLGenerator.createSql(
    template,
    sessionTemplateColumns,
    groupValuesMap,
  )

  val outputFile = File(path + "R__Session_Template_Data.sql")
  outputFile.delete()
  PrintWriter(outputFile).use { out ->
    out.print(sql)
    print("File created : ${outputFile.absolutePath}!")
  }
}
