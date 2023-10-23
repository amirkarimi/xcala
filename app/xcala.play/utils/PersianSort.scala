package xcala.play.utils

import java.util.Locale

import com.ibm.icu.text.Collator

object PersianSort extends Ordering[String] {

  private val persianCollator: Collator =
    Collator.getInstance(new Locale("fa")).freeze()

  override def compare(x: String, y: String): Int =
    persianCollator.compare(x, y)

}
