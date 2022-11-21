package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.DAY_OF_WEEK
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.END_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.END_TIME
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.LOCATION_KEYS
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.ROOM
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.START_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.START_TIME
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_COLUMNS.TYPE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_LOCATION_COLUMNS.KEY
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_LOCATION_COLUMNS.LEVEL_FOUR
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_LOCATION_COLUMNS.LEVEL_ONE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_LOCATION_COLUMNS.LEVEL_THREE
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SESSION_LOCATION_COLUMNS.LEVEL_TWO
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.stream.Collectors
import kotlin.collections.ArrayList

class SessionTemplateSQLGenerator {

  enum class SESSION_COLUMNS {
    PRISON, ROOM, TYPE, OPEN, CLOSED, START_TIME, END_TIME, START_DATE, END_DATE, DAY_OF_WEEK, LOCATION_KEYS;
  }

  enum class SESSION_LOCATION_COLUMNS {
    PRISON, KEY, LEVEL_ONE, LEVEL_TWO, LEVEL_THREE, LEVEL_FOUR;
  }

  companion object {
    private const val DELIMITER = ":"
    const val SPACE_TAB = '\t'
    val LINE_SEPARATOR: String = System.getProperty("line.separator")
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
      val values = value?.let { it.uppercase().split(DELIMITER).toSet() } ?: run { listOf<String>() }
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
    val locationKeys: String?
  ) {
    constructor(sessionRecord: CSVRecord) : this(
      prison = sessionRecord.get(SESSION_COLUMNS.PRISON.name).uppercase(),
      room = sessionRecord.get(ROOM.name),
      type = VisitType.valueOf(sessionRecord.get(TYPE.name).uppercase()),
      open = Integer.parseInt(sessionRecord.get(OPEN.name)),
      closed = Integer.parseInt(sessionRecord.get(CLOSED.name)),
      startTime = LocalTime.parse(sessionRecord.get(START_TIME.name)),
      endTime = LocalTime.parse(sessionRecord.get(END_TIME.name)),
      startDate = LocalDate.parse(sessionRecord.get(START_DATE.name)),
      endDate = sessionRecord.get(END_DATE.name)?.let { LocalDate.parse(it) },
      dayOfWeek = DayOfWeek.valueOf(sessionRecord.get(DAY_OF_WEEK.name).uppercase()),
      locationKeys = sessionRecord.get(LOCATION_KEYS.name)?.let { it.uppercase() }
    )
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
      prison = sessionRecord.get(SESSION_LOCATION_COLUMNS.PRISON.name).uppercase(),
      key = sessionRecord.get(KEY.name).uppercase(),
      levelOne = toList(sessionRecord.get(LEVEL_ONE.name)),
      levelTwo = toList(sessionRecord.get(LEVEL_TWO.name)),
      levelThree = toList(sessionRecord.get(LEVEL_THREE.name)),
      levelFour = toList(sessionRecord.get(LEVEL_FOUR.name))
    )
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
    sessionRecords: List<SessionTemplateColumns>,
    sessionLocationItems: List<SessionLocationItem>,
  ): String {
    val output = StringBuilder()
    addLineNoTab(buffer = output)
    addLineNoTab("-- This script clears certain tables and re-set auto id's to zero!", output)
    addLineNoTab("-- WARNING if the session template id's are used in other tables this script might have to change!", output)
    addLineNoTab("-- This is a temporary solution, and should be replaced by a JSON admin API!", output)
    addLineNoTab("BEGIN;", output)
    addLine(buffer = output)
    addLine("SET SCHEMA 'public';", output)
    addLine(buffer = output)
    addLine(createTruncateSQL(), output)
    addLine(buffer = output)
    addLineNoTab(createSessionTemplateInsertSQLs(sessionRecords), output)
    addLine(buffer = output)
    addLineNoTab(createPermittedSessionLocationInsertSQL(sessionLocationItems), output)
    addLine(buffer = output)
    addLineNoTab(createLinkTableDataSql(), output)
    addLine(buffer = output)
    addLine("-- Drop temporary tables", output)
    addLine("DROP TABLE tmp_session_template;", output)
    addLine("DROP TABLE tmp_permitted_session_location;", output)
    addLineNoTab(buffer = output)
    addLineNoTab("END;", output)
    addLineNoTab(buffer = output)

    return output.toString()
  }

  private fun createTruncateSQL(): String {
    val sqlInsertBuilder = StringBuilder()
    addLine("-- Use TRUNCATE rather than delete so indexes are re-set", sqlInsertBuilder)
    addLine(" TRUNCATE TABLE session_to_permitted_location RESTART IDENTITY CASCADE;", sqlInsertBuilder)
    addLine(" TRUNCATE TABLE session_template  RESTART IDENTITY CASCADE;", sqlInsertBuilder)
    addLine(" TRUNCATE TABLE permitted_session_location  RESTART IDENTITY CASCADE;", sqlInsertBuilder)
    return sqlInsertBuilder.toString()
  }

  private fun createSessionTemplateInsertSQLs(sessionRecords: List<SessionTemplateColumns>): String {

    val sqlInsertBuilder = StringBuilder()
    addLine("-- Creating session template data", sqlInsertBuilder)
    addLine("CREATE TEMP TABLE tmp_session_template(", sqlInsertBuilder)
    addLine(" id                serial        NOT NULL PRIMARY KEY,", sqlInsertBuilder)
    addLine(" locationKeys      VARCHAR       ,", sqlInsertBuilder)
    addLine(" prison_code       VARCHAR(6)    NOT NULL,", sqlInsertBuilder)
    addLine(" prison_id         int    ,", sqlInsertBuilder)
    addLine(" visit_room        VARCHAR(255)  NOT NULL,", sqlInsertBuilder)
    addLine(" visit_type        VARCHAR(80)   NOT NULL,", sqlInsertBuilder)
    addLine(" open_capacity     integer       NOT NULL,", sqlInsertBuilder)
    addLine(" closed_capacity   integer       NOT NULL,", sqlInsertBuilder)
    addLine(" start_time        time          NOT NULL,", sqlInsertBuilder)
    addLine(" end_time          time          NOT NULL,", sqlInsertBuilder)
    addLine(" valid_from_date   date          NOT NULL,", sqlInsertBuilder)
    addLine(" valid_to_date     date          ,", sqlInsertBuilder)
    addLine(" day_of_week       VARCHAR(40)", sqlInsertBuilder)
    addLine(");", sqlInsertBuilder)
    addLine(buffer = sqlInsertBuilder)
    addLine(
      "INSERT INTO tmp_session_template (locationKeys,prison_code, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week) ",
      sqlInsertBuilder
    )
    addLine("VALUES", sqlInsertBuilder)
    for (sessionRecord in sessionRecords) {
      with(sessionRecord) {
        val valueRow = StringBuilder()
        valueRow.append("(${addStringValueSql(locationKeys)},'$prison','$room', '$type', $open, $closed, '$startTime', '$endTime', '$startDate', $endDate, '$dayOfWeek')")
        if (sessionRecords.last() != sessionRecord) {
          valueRow.append(",")
        } else {
          valueRow.append(";")
        }
        addLine(valueRow.toString(), sqlInsertBuilder)
      }
    }

    addLine(buffer = sqlInsertBuilder)
    addLine("UPDATE tmp_session_template SET prison_id = prison.id FROM prison WHERE tmp_session_template.prison_code = prison.code;", sqlInsertBuilder)
    addLine(buffer = sqlInsertBuilder)
    addLine("INSERT INTO session_template(id,visit_room,visit_type,open_capacity,closed_capacity,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id)", sqlInsertBuilder)
    addLine("   SELECT id,visit_room,visit_type,open_capacity,closed_capacity,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id FROM tmp_session_template;", sqlInsertBuilder)

    addLine("ALTER SEQUENCE session_template_id_seq RESTART WITH ${sessionRecords.size + 1};", sqlInsertBuilder)

    return sqlInsertBuilder.toString()
  }

  private fun createPermittedSessionLocationInsertSQL(sessionLocationItems: List<SessionLocationItem>): String {

    val sqlInsertBuilder = StringBuilder()
    addLine("-- Create permitted session location data", sqlInsertBuilder)
    addLine("CREATE TABLE tmp_permitted_session_location (", sqlInsertBuilder)
    addLine(" id                serial        NOT NULL PRIMARY KEY,", sqlInsertBuilder)
    addLine(" key               VARCHAR(20)   NOT NULL,", sqlInsertBuilder)
    addLine(" prison_code       VARCHAR(6)    NOT NULL,", sqlInsertBuilder)
    addLine(" prison_id         int,", sqlInsertBuilder)
    addLine(" level_one_code    VARCHAR(10) NOT NULL,", sqlInsertBuilder)
    addLine(" level_two_code    VARCHAR(10),", sqlInsertBuilder)
    addLine(" level_three_code  VARCHAR(10),", sqlInsertBuilder)
    addLine(" level_four_code   VARCHAR(10)", sqlInsertBuilder)
    addLine(");", sqlInsertBuilder)
    addLine(buffer = sqlInsertBuilder)

    val columns = StringBuilder()
    columns.append("INSERT INTO tmp_permitted_session_location (key,prison_code,level_one_code")
    columns.append(',').append("level_two_code")
    columns.append(',').append("level_three_code")
    columns.append(',').append("level_four_code")
    columns.append(") ")
    addLine(columns.toString(), sqlInsertBuilder)
    addLine("VALUES", sqlInsertBuilder)

    sessionLocationItems.forEach { sessionLocationItem ->
      run {

        with(sessionLocationItem) {
          val values = StringBuilder()
          values.append("('$key',")
          values.append("'$prison'")
          values.append(',').append(addStringValueSql(levelOne))
          values.append(',').append(addStringValueSql(levelTwo))
          values.append(',').append(addStringValueSql(levelThree))
          values.append(',').append(addStringValueSql(levelFour))
          values.append(')')
          if (sessionLocationItems.last() != sessionLocationItem) {
            values.append(",")
          } else {
            values.append(";")
          }
          addLine(values.toString(), sqlInsertBuilder)
        }
      }
    }

    addLine(buffer = sqlInsertBuilder)
    addLine("UPDATE tmp_permitted_session_location SET prison_id = prison.id FROM prison WHERE tmp_permitted_session_location.prison_code = prison.code;", sqlInsertBuilder)
    addLine(buffer = sqlInsertBuilder)
    addLine("INSERT INTO permitted_session_location(id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code)", sqlInsertBuilder)
    addLine("   SELECT id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code FROM tmp_permitted_session_location;", sqlInsertBuilder)

    addLine(buffer = sqlInsertBuilder)
    addLine("ALTER SEQUENCE permitted_session_location_id_seq RESTART WITH ${sessionLocationItems.size + 1};", sqlInsertBuilder)

    return sqlInsertBuilder.toString()
  }

  private fun createLinkTableDataSql(): String {
    val sqlInsertBuilder = StringBuilder()
    addLine("-- Create link table data", sqlInsertBuilder)
    addLine("INSERT INTO session_to_permitted_location(session_template_id, permitted_session_location_id)", sqlInsertBuilder)
    addLine("  SELECT st.id, l.id FROM tmp_session_template st ", sqlInsertBuilder)
    addLine("     JOIN tmp_permitted_session_location l ON POSITION(l.key  IN st.locationKeys)<>0 ORDER BY st.id,l.id;", sqlInsertBuilder)
    return sqlInsertBuilder.toString()
  }

  private fun addStringValueSql(value: String?): String {
    value?.let {
      return "'$value'"
    } ?: run {
      return "null"
    }
  }

  private fun addLineNoTab(value: String? = null, buffer: StringBuilder) {
    value?.let { buffer.append(value) }
    buffer.append(LINE_SEPARATOR)
  }

  private fun addLine(value: String? = null, buffer: StringBuilder) {
    value?.let { buffer.append(SPACE_TAB) }
    addLineNoTab(value, buffer)
  }
}

fun main(args: Array<String>) {
  val path = "src/main/resources/session-template-data/"
  val sessionDataFile = File(path, "session-data.csv")
  val sessionLocationDataFile = File(path, "session-location-data.csv")

  val sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()

  val sessionLocationItems = sessionTemplateSQLGenerator.getSessionLocationItems(sessionLocationDataFile)
  val sessionRecords = sessionTemplateSQLGenerator.getSessionRecordsRecords(sessionDataFile)

  val sql = sessionTemplateSQLGenerator.createSql(sessionRecords, sessionLocationItems)

  System.out.print(sql)
}
