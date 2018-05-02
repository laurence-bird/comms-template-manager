package views

import java.time.{Instant, LocalDate}

import com.ovoenergy.comms.model.{Channel, Print}
import models.TemplateVersion

object ViewHelper {

  def formatDate(instant: Instant): String =
    instant.toString

  def formatOptDate(opt: Option[Instant]): String =
    opt.map(formatDate).getOrElse("(unknown)")

  def dateToLong(instant: Instant): Long = {
    instant.toEpochMilli
  }

  def commPerformanceLink(commName: String, commPerformanceUrl: String): String = {
    val start = LocalDate.now().minusDays(1).toString
    s"$commPerformanceUrl?commName=$commName&timePeriod=Day&start=$start"
  }

  def commSearchLink(commName: String, commSearchUrl: String): String =
    s"$commSearchUrl?commName=$commName"

  def containsPrintTemplates(templates: Seq[TemplateVersion]) = {
    templates.foldLeft(false) { (acc, templateVersion) =>
      containsPrintTemplate(templateVersion) && acc
    }
  }

  def containsPrintTemplate(template: TemplateVersion) = {
    template.channels
      .exists(_.contains(Print))
  }

}
