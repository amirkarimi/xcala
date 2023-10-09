package xcala.play.postgres.utils

import xcala.play.postgres.extensions.FormHelper._
import xcala.play.postgres.models.Range

import play.api.data.Forms._
import play.api.data.Mapping

import org.joda.time.{DateTime, LocalDate}

object FormHelper {

  private val persianEnglishNumbersMapping: Map[Char, Char] = Map(
    '۰' -> '0',
    '۱' -> '1',
    '۲' -> '2',
    '۳' -> '3',
    '۴' -> '4',
    '۵' -> '5',
    '۶' -> '6',
    '۷' -> '7',
    '۸' -> '8',
    '۹' -> '9'
  )

  def removeSpecialCharacters(mapping: Mapping[String]): Mapping[String] = {
    mapping.transform[String](a => KeywordExtractor.removeSpecialCharacters(a.trim), a => a)
  }

  def convertPersianNumbersToEnglish(mapping: Mapping[String]): Mapping[String] = {
    def convertStringOfPersianNumbersToEnglish(a: String) = {
      persianEnglishNumbersMapping.foldLeft(a) { case (str, (persian, english)) =>
        str.replace(persian, english)
      }
    }

    mapping.transform[String](a => convertStringOfPersianNumbersToEnglish(a.trim), a => a)
  }

  lazy val dateRangeMapping: Mapping[Range[Option[LocalDate]]] = mapping(
    "from" -> optional(jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd")),
    "to"   -> optional(jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd"))
  )(Range.apply[Option[LocalDate]])(Range.unapply[Option[LocalDate]])

  lazy val dateRangeMappingNonOptional: Mapping[Range[LocalDate]] = mapping(
    "from" -> jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd"),
    "to"   -> jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd")
  )(Range.apply[LocalDate])(Range.unapply[LocalDate])

  lazy val dateTimeRangeMapping: Mapping[Range[Option[DateTime]]] = mapping(
    "from" -> optional(jodaDateTimeMappingWithYearRestriction(pattern = "yyyy-MM-dd HH:mm")),
    "to"   -> optional(jodaDateTimeMappingWithYearRestriction(pattern = "yyyy-MM-dd HH:mm"))
  )(Range.apply[Option[DateTime]])(Range.unapply[Option[DateTime]])

}
