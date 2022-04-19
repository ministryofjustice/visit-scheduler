package uk.gov.justice.digital.hmpps.visitscheduler.utils

import kotlin.random.Random

class QuotableEncoder(private val delimiter: String = "-", private val minLength: Int = 1, private val chunkSize: Int = 2) {

  companion object {
    private val SEPARATOR = arrayOf("c", "f", "h", "u", "i", "t")
    private val SEED = arrayOf("z", "a", "l", "y", "x", "m", "q", "r", "b", "o", "d", "s", "n", "p", "e", "g", "j", "v", "w", "k")
  }

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
    value.toString(SEED.size).toCharArray().forEach {
      hash += SEED[it.digitToInt(SEED.size)]
    }

    var hashPadded = hash.reversed()
    if (hashPadded.length < minLength || hashPadded.length < chunkSize || hashPadded.length % chunkSize > 0) {
      hashPadded += SEPARATOR[Random.nextInt(0, SEPARATOR.size - 1)]
      while (hashPadded.length < minLength || hashPadded.length < chunkSize || hashPadded.length % chunkSize > 0) {
        hashPadded += SEED[Random.nextInt(0, SEED.size - 1)]
      }
    }

    return hashPadded.chunked(chunkSize).joinToString(delimiter)
  }

  fun decode(encoded: String): Long {
    var hashPadded = encoded.split(delimiter).joinToString("")
    SEPARATOR.forEach {
      hashPadded = hashPadded.split(it)[0]
    }

    var convertedValue = ""
    hashPadded.reversed().toCharArray().forEach {
      convertedValue += SEED.indexOf(it.toString()).toString(SEED.size)
    }

    return convertedValue.toLong(SEED.size)
  }
}
