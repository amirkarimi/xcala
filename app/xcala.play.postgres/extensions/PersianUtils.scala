package xcala.play.postgres.extensions

import play.api.i18n.Messages

import com.bahmanm.persianutils.DateConverter._
import org.joda.time.DateTime

object PersianUtils {

  private final val monthNames: IndexedSeq[String] =
    IndexedSeq("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

  implicit class PersianDateConverter(val dateTime: DateTime) extends AnyVal {

    def toGlobalDateTimeString(
        addTime: Boolean = true,
        reversed: Boolean = false,
        faSeparator: String = "-",
        nonFaSeparator: String = " "
    )(implicit
        messages: Messages
    ): String = {
      if (messages.lang.code == "fa") {
        val gDate: SimpleDate =
          SimpleDate(year = dateTime.year.get, month = dateTime.monthOfYear.get, day = dateTime.dayOfMonth.get)

        val pDate: SimpleDate =
          gregorianToPersian(gDate)

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

    def toGlobalLongDateTimeString(implicit messages: Messages, addTime: Boolean = true): String = {
      if (messages.lang.code == "fa") {
        val gDate: SimpleDate =
          SimpleDate(year = dateTime.year.get, month = dateTime.monthOfYear.get, day = dateTime.dayOfMonth.get)

        val pDate: SimpleDate =
          gregorianToPersian(gDate)

        s"${pDate.day} ${monthNames(pDate.month - 1)} ${pDate.year}" +
        (if (addTime) { " " + dateTime.toString("HH:mm") }
         else { "" })
      } else {
        dateTime.toString("MMMM dd, yyyy" + (if (addTime) { " HH:mm" }
                                             else { "" }))
      }
    }

    def toGlobalDateString(implicit messages: Messages): String = toGlobalDateTimeString(addTime = false)(messages)

    def toGlobalLongDateString(implicit messages: Messages): String = toGlobalLongDateTimeString(messages, false)

    def toGlobalYear(implicit messages: Messages): Int = {
      if (messages.lang.code == "fa") {
        val gDate: SimpleDate =
          SimpleDate(year = dateTime.year.get, month = dateTime.monthOfYear.get, day = dateTime.dayOfMonth.get)
        val pDate: SimpleDate =
          gregorianToPersian(gDate)

        pDate.year
      } else {
        dateTime.getYear
      }
    }

    def toGlobalMonthName(implicit messages: Messages): String = {
      if (messages.lang.code == "fa") {
        val pDate: SimpleDate = gregorianToPersian(
          SimpleDate(year = dateTime.year.get, month = dateTime.monthOfYear.get, day = dateTime.dayOfMonth.get)
        )
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
