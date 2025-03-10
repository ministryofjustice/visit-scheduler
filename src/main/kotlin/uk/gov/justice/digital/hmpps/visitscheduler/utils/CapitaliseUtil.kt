package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Service
import java.util.Locale

@Service
class CapitaliseUtil {
  fun capitalise(sentence: String): String = sentence.lowercase(Locale.getDefault()).split(" ").joinToString(" ") { word ->
    var index = 0
    for (ch in word) {
      if (ch in 'a'..'z') {
        break
      }
      index++
    }
    if (index < word.length) {
      word.replaceRange(index, index + 1, word[index].titlecase(Locale.getDefault()))
    } else {
      word
    }
  }
}
