package models

import java.time.Instant

import com.ovoenergy.comms.model.CommType

case class TemplateVersion(commName: String, version: String, publishedAt: Instant, publishedBy: String, commType: CommType)

case class TemplateSummary(commName: String, commType: CommType, latestVersion: String)



