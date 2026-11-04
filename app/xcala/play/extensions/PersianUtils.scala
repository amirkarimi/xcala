package xcala.play.extensions

import play.api.i18n.Messages

import scala.util.Try

import com.bahmanm.persianutils.DateConverter._
import org.joda.time.DateTime

object PersianUtils {

  private final val monthNames: IndexedSeq[String] =
    IndexedSeq(
      "فروردین",
      "اردیبهشت",
      "خرداد",
      "تیر",
      "مرداد",
      "شهریور",
      "مهر",
      "آبان",
      "آذر",
      "دی",
      "بهمن",
      "اسفند"
    )

  implicit class PersianDateConverter(val dateTime: DateTime) extends AnyVal {

    def toGlobalDateTimeString(
        addTime       : Boolean = true,
        reversed      : Boolean = false,
        faSeparator   : String  = "-",
        nonFaSeparator: String  = " "
    )(implicit
        messages: Messages
    ): String = {
      if (messages.lang.code == "fa") {
        val gDate =
          SimpleDate(
            year  = dateTime.year.get,
            month = dateTime.monthOfYear.get,
            day   = dateTime.dayOfMonth.get
          )
        val pDate = gregorianToPersian(gDate)
        val parts: Seq[String] =
          Seq(
            s"${pDate.year}/${pDate.month.toString.reverse.padTo(2, '0').reverse.mkString}/${pDate.day.toString.reverse.padTo(2, '0').reverse.mkString}"
          ) ++ {
            if (addTime) {
              dateTime.toString(
                "HH:mm"
              ) :: Nil
            } else {
              Nil
            }
          }
        {
          if (reversed) {
            parts.reverse
          } else {
            parts
          }
        }.mkString(faSeparator)

      } else {
        val parts: Seq[String] =
          Seq("yyyy-MM-dd", "HH:mm")

        dateTime.toString(
          {
            if (reversed) {
              parts.reverse
            } else {
              parts
            }
          }.mkString(nonFaSeparator)
        )
      }
    }

    def toGlobalDateTimeStringOption(
        addTime       : Boolean = true,
        reversed      : Boolean = false,
        faSeparator   : String  = "-",
        nonFaSeparator: String  = " "
    )(implicit
        messages: Messages
    ): Option[String] = Try {
      toGlobalDateTimeString(
        addTime        = addTime,
        reversed       = reversed,
        faSeparator    = faSeparator,
        nonFaSeparator = nonFaSeparator
      )
    }.toOption

    /** Format => yyyy-MM-dd HH:mm
      * @param addTime
      *   if true return with time
      * @return
      */
    def toJalaliDateTimeString(addTime: Boolean = true): String = {
      val gDate =
        SimpleDate(year = dateTime.year.get, month = dateTime.monthOfYear.get, day = dateTime.dayOfMonth.get)
      val pDate = gregorianToPersian(gDate)
      s"${pDate.year}-${pDate.month.toString.reverse.padTo(2, '0').reverse.mkString}-${pDate.day.toString.reverse.padTo(2, '0').reverse.mkString}" +
      (if (addTime) { " " + dateTime.toString("HH:mm") }
       else { "" })
    }

    def toGlobalLongDateTimeString(implicit messages: Messages, addTime: Boolean = true): String = {
      if (messages.lang.code == "fa") {
        val gDate =
          SimpleDate(
            year  = dateTime.year.get,
            month = dateTime.monthOfYear.get,
            day   = dateTime.dayOfMonth.get
          )
        val pDate = gregorianToPersian(gDate)
        s"${pDate.day} ${monthNames(pDate.month - 1)} ${pDate.year}" +
        (if (addTime) { " " + dateTime.toString("HH:mm") }
         else { "" })
      } else {
        dateTime.toString("MMMM dd, yyyy" +
          (if (addTime) { " HH:mm" }
           else { "" }))
      }
    }

    def toGlobalDateStringOption(implicit messages: Messages): Option[String] =
      toGlobalDateTimeStringOption(addTime = false)(messages)

    def toGlobalDateString(implicit messages: Messages): String =
      toGlobalDateTimeString(addTime = false)(messages)

    def toGlobalLongDateString(implicit messages: Messages): String =
      toGlobalLongDateTimeString(messages, addTime = false)

    def toGlobalYear(implicit messages: Messages): Int = {
      if (messages.lang.code == "fa") {
        val gDate =
          SimpleDate(
            year  = dateTime.year.get,
            month = dateTime.monthOfYear.get,
            day   = dateTime.dayOfMonth.get
          )
        val pDate = gregorianToPersian(gDate)
        pDate.year
      } else {
        dateTime.getYear
      }
    }

    def toGlobalMonthName(implicit messages: Messages): String = {
      if (messages.lang.code == "fa") {
        val gDate =
          SimpleDate(
            year  = dateTime.year.get,
            month = dateTime.monthOfYear.get,
            day   = dateTime.dayOfMonth.get
          )
        val pDate = gregorianToPersian(gDate)
        monthNames(pDate.month - 1)
      } else {
        dateTime.toString("MMM")
      }
    }

  }

  def convertToPersianChars(text: String): String = {
    text
      .replace("ي", "ی")
      .replace("ك", "ک")
  }

}
