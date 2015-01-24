package xcala.play.extensions

import org.joda.time.DateTime
import play.api.i18n.Lang
import com.bahmanm.persianutils.DateConverter._

object PersianUtils {
  private final val monthNames = List("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
	  
	implicit class PersianDateConverter(val dateTime: DateTime) extends AnyVal {
	  def toGlobalDateTimeString(implicit lang: Lang, addTime: Boolean = true) = {
	    if (lang.code == "fa") {
	      val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
	      val pDate = gregorianToPersian(gDate)
	      s"${pDate.year}/${pDate.month}/${pDate.day} " + 
      		(if (addTime) { dateTime.toString("HH:mm") } else { "" })
	    } else {
	      dateTime.toString("yyyy-MM-dd" + (if (addTime) { " HH:mm" } else { "" }))
	    }
	  }
	  
	  def toGlobalDateString(implicit lang: Lang) = toGlobalDateTimeString(lang, false)
	  
	  def toGlobalYear(implicit lang: Lang): Int = {
	    if (lang.code == "fa") {
	      val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
	      val pDate = gregorianToPersian(gDate)
	      pDate.year
	    } else {
	      dateTime.getYear
	    }
	  }
	  
	  def toGlobalMonthName(implicit lang: Lang): String = {
	    if (lang.code == "fa") {
	      val gDate = SimpleDate(dateTime.year.get, dateTime.monthOfYear.get, dateTime.dayOfMonth.get)
	      val pDate = gregorianToPersian(gDate)
	      monthNames(pDate.month - 1)
	    } else {
	      dateTime.toString("MMM")
	    }	    
	  }
	}
}