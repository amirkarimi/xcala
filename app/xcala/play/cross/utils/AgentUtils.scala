package xcala.play.cross.utils

import play.api.Configuration

import org.joda.time.DateTime
import org.joda.time.LocalTime

object AgentUtils {

  def getInitialDuration(startTimeConfig: String, defaultStartTime: String)(implicit
      configuration: Configuration
  ): Long = {
    val startHour =
      LocalTime.parse(configuration.get[Option[String]](startTimeConfig).getOrElse(defaultStartTime))
    val now     = DateTime.now
    val dueTime = now.toLocalDate.toDateTimeAtStartOfDay.withTime(startHour)

    if (now.isAfter(dueTime)) {
      24 * 60 * 60 * 1000 - (now.getMillis - dueTime.getMillis)
    } else {
      dueTime.getMillis - now.getMillis
    }
  }

}
