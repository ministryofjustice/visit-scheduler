package uk.gov.justice.digital.hmpps.visitscheduler.utils

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

data class Data(
  var location: String,
  var prisonCode: String,
  var prisonerId: String,
  var sessionInfo: String,
  var category: String,
  var incentiveLevel: String,
  var room: String,
) {

  fun hasMovedPrisons(): Boolean {
    return prisonCode != location
  }
}

fun main() {
  val dir = "./src/main/resources/session-template-data/"
  val file = File(dir + "results.csv")
  val lines = Files.readAllLines(file.toPath(), Charset.defaultCharset())

  val dataList = mutableListOf<Data>()

  lines.forEach {
    val location = getItem(it, "prisonCode=", 3)
    val prisonCode = getItem(it, "prison code ", 3)
    val prisonerId = getItem(it, "prisoner id ", 7)
    val sessionInfo = getItem(it, "visit ", " room:")
    val category = getItem(it, "category=", 1)
    val incentiveLevel = getItem(it, "incentiveLevel=", ", prison")
    val room = getItem(it, " room:", "!")

    val data = Data(
      location = location,
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      sessionInfo = sessionInfo,
      category = category,
      incentiveLevel = incentiveLevel,
      room = room,
    )

    dataList.add(data)
  }

  dataList.sortBy { it.sessionInfo }
  val dataListFiltered = dataList.filter { it.prisonerId.length > 0 && !it.sessionInfo.contains("2023-05-17") }

  val builder = StringBuilder()

  val prisoners = dataListFiltered.map { it -> it.prisonerId }.toSet()
  addLine(builder)
  addLine(builder, "Prisoners affected: ${prisoners.size}")
  addLine(builder)
  prisoners.forEach {
    add(builder, "$it, ")
  }
  addLine(builder)
  addLine(builder)
  addLine(builder, "Prisoner Sessions: ${dataListFiltered.size}")
  addLine(builder)

  val dataListAtPrison = dataListFiltered.filter { !it.hasMovedPrisons() }
  addLine(builder, " Prisoner still at DMI: ${dataListAtPrison.size}")
  addLine(builder)
  dataListAtPrison.forEach {
    with(it) {
      addLine(builder, " PrisonerId :\t$prisonerId")
      addLine(builder, "\tCategory       :\t$category")
      addLine(builder, "\tSessionInfo    :\t$sessionInfo")
      addLine(builder, "\tIncentiveLevel :\t$incentiveLevel")
      addLine(builder, "\tRoom           :\t$room")
      addLine(builder)
    }
  }
  addLine(builder)
  val dataListNotAtPrison = dataListFiltered.filter { it.hasMovedPrisons() }
  addLine(builder)
  addLine(builder, " Prisoner that maynot me at DMI: ${dataListNotAtPrison.size}")
  addLine(builder)
  dataListNotAtPrison.forEach {
    with(it) {
      addLine(builder, " PrisonerId :\t$prisonerId")
      addLine(builder, "\tPrison code    :\t$location")
      addLine(builder, "\tCategory       :\t$category")
      addLine(builder, "\tSessionInfo    :\t$sessionInfo")
      addLine(builder, "\tIncentiveLevel :\t$incentiveLevel")
      addLine(builder, "\tRoom           :\t$room")
      addLine(builder)
    }
  }

  System.out.print(builder.toString())
}

private fun add(builder: StringBuilder, value: String = "") {
  builder.append(value)
}

private fun addLine(builder: StringBuilder, value: String = "") {
  add(builder, value)
  builder.append(System.getProperty("line.separator"))
}

private fun getItem(it: String, key: String, endKey: String): String {
  val startOfKey = it.indexOf(key)
  val endOfKey = it.indexOf(endKey, startOfKey)

  if (startOfKey > -1 && endOfKey > -1) {
    val startOf = startOfKey + key.length
    return it.substring(startOf, endOfKey)
  }
  return ""
}

private fun getItem(it: String, key: String, length: Int): String {
  val startOfKey = it.indexOf(key)
  if (startOfKey > -1) {
    val startOf = startOfKey + key.length
    return it.substring(startOf, startOf + length)
  }
  return ""
}
