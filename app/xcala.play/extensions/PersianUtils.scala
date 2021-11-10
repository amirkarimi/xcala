package xcala.play.extensions

import org.joda.time.DateTime
import play.api.i18n.Messages
import com.bahmanm.persianutils.DateConverter._

object PersianUtils {
	private final val monthNames = List("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

	implicit class PersianDateConverter(val dateTime: DateTime) extends AnyVal {
		def toGlobalDateTimeString(implicit messages: Messages, addTime: Boolean = true) = {
			if (messages.lang.code == "fa") {
				val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
				val pDate = gregorianToPersian(gDate)
				s"${pDate.year}/${pDate.month.toString.reverse.padTo(2, "0").reverse.mkString}/${pDate.day.toString.reverse.padTo(2, "0").reverse.mkString}" +
					(if (addTime) { " " + dateTime.toString("HH:mm") } else { "" })
			} else {
				dateTime.toString("yyyy-MM-dd" + (if (addTime) { " HH:mm" } else { "" }))
			}
		}

		/**
		 * Format => yyyy-MM-dd HH:mm
		 * @param addTime
		 * @return
		 */
		def toJalaliDateTimeString(addTime: Boolean = true) = {
			val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
			val pDate = gregorianToPersian(gDate)
			s"${pDate.year}-${pDate.month.toString.reverse.padTo(2, "0").reverse.mkString}-${pDate.day.toString.reverse.padTo(2, "0").reverse.mkString}" +
				(if (addTime) { " " + dateTime.toString("HH:mm") } else { "" })
		}

		def toGlobalLongDateTimeString(implicit messages: Messages, addTime: Boolean = true) = {
			if (messages.lang.code == "fa") {
				val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
				val pDate = gregorianToPersian(gDate)
				s"${pDate.day} ${monthNames(pDate.month - 1)} ${pDate.year}" +
					(if (addTime) { " " + dateTime.toString("HH:mm") } else { "" })
			} else {
				dateTime.toString("MMMM dd, yyyy" + (if (addTime) { " HH:mm" } else { "" }))
			}
		}

		def toGlobalDateString(implicit messages: Messages) = toGlobalDateTimeString(messages, false)

		def toGlobalLongDateString(implicit messages: Messages) = toGlobalLongDateTimeString(messages, false)

		def toGlobalYear(implicit messages: Messages): Int = {
			if (messages.lang.code == "fa") {
				val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
				val pDate = gregorianToPersian(gDate)
				pDate.year
			} else {
				dateTime.getYear
			}
		}

		def toGlobalMonthName(implicit messages: Messages): String = {
			if (messages.lang.code == "fa") {
				val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
				val pDate = gregorianToPersian(gDate)
				monthNames(pDate.month - 1)
			} else {
				dateTime.toString("MMM")
			}
		}
	}

	def convertToPersianChars(text: String) = {
		text
			.replace("ي", "ی")
			.replace("ك", "ک")
	}
}