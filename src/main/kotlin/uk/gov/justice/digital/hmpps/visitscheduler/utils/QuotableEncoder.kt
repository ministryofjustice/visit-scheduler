package uk.gov.justice.digital.hmpps.visitscheduler.utils

import kotlin.random.Random

class QuotableEncoder(private val delimiter: String = "-", private val minLength: Int = 1, private val chunkSize: Int = 2) {

  private val seed = arrayOf("z", "a", "l", "y", "x", "m", "q", "r", "b", "o", "d", "s", "n", "p", "e", "g", "j", "v", "w", "k")
  private val separator = arrayOf("c", "f", "h", "u", "i", "t")

  init {
    require(delimiter.length in 0..1) {
      "delimiter length must be zero or one"
    }
    require(delimiter.isEmpty() || !delimiter.all { it.isLetterOrDigit() }) {
      "delimiter must not contain alphanumeric characters"
    }
    require(minLength > 0) {
      "minimum length must be greater than zero"
    }
    require(chunkSize > 0) {
      "minimum chunk size must be greater than zero"
    }
  }

  fun encode(value: Long): String {
    var hash = ""
    value.toString(seed.size).toCharArray().forEach {
      hash += seed[it.digitToInt(seed.size)]
    }

    var hashPadded = hash.reversed()
    if (hashPadded.length < minLength || hashPadded.length < chunkSize || hashPadded.length % chunkSize > 0) {
      hashPadded += separator[Random.nextInt(0, separator.size - 1)]
      while (hashPadded.length < minLength || hashPadded.length < chunkSize || hashPadded.length % chunkSize > 0) {
        hashPadded += seed[Random.nextInt(0, seed.size - 1)]
      }
    }

    return hashPadded.chunked(chunkSize).joinToString(delimiter)
  }

  fun decode(encoded: String): Long {
    var hashPadded = encoded.split(delimiter).joinToString("")
    separator.forEach {
      hashPadded = hashPadded.split(it)[0]
    }

    var convertedValue = ""
    hashPadded.reversed().toCharArray().forEach {
      convertedValue += seed.indexOf(it.toString()).toString(seed.size)
    }

    return convertedValue.toLong(seed.size)
  }
}
