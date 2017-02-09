package views

import java.time.Instant

object ViewHelper {

  def formatDate(instant: Instant): String =
    instant.toString

  def formatOptDate(opt: Option[Instant]): String =
    opt.map(formatDate).getOrElse("(unknown)")

  def dateToLong(instant: Instant): Long = {
    instant.toEpochMilli
  }

}
