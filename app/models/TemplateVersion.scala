package models

import java.time.Instant

import com.ovoenergy.comms.model.{CommManifest, CommType}


case class TemplateVersion(commName: String, version: String, publishedAt: Instant, publishedBy: String, commType: CommType)

object TemplateVersion{
  def apply(commManifest: CommManifest, publishedBy: String): TemplateVersion = {
    TemplateVersion(
      commManifest.name,
      commManifest.version,
      Instant.now(),
      publishedBy,
      commManifest.commType
    )
  }
}

case class ZippedRawTemplate(templateFiles: Array[Byte])

case class TemplateSummary(commName: String, commType: CommType, latestVersion: String)

object TemplateSummary{
  def apply(commManifest: CommManifest): TemplateSummary = {
    TemplateSummary(
      commManifest.name,
      commManifest.commType,
      commManifest.version
    )
  }
}
