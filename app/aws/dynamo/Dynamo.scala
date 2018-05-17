package aws.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.scanamo._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.error.DynamoReadError._
import com.gu.scanamo.syntax._
import com.ovoenergy.comms.model.{Channel, CommType, TemplateManifest}
import models.{Brand, TemplateSummary, TemplateVersion}
import play.api.Logger

class Dynamo(db: AmazonDynamoDB,
             templateVersionTable: Table[TemplateVersion],
             templateSummaryTable: Table[TemplateSummary]) {

  def listVersions(templateId: String): Seq[TemplateVersion] = {
    val query = templateVersionTable.query('templateId -> templateId)
    Scanamo.exec(db)(query).flatMap { result =>
      logIfError(result).toOption
    }
  }

  //TODO - Not a conditional write as desiredn
  def writeNewVersion(templateManifest: TemplateManifest,
                      commName: String,
                      commType: CommType,
                      brand: Brand,
                      publishedBy: String,
                      channels: List[Channel]): Either[String, Unit] = {

    if (isNewestVersion(templateManifest)) {

      val templateVersion = TemplateVersion(
        templateManifest,
        commName,
        commType,
        publishedBy,
        channels
      )

      Scanamo.exec(db)(templateVersionTable.put(templateVersion))
      Logger.info(s"Written template version to persistence $templateVersion")

      val templateSummary = TemplateSummary(
        templateManifest.id,
        commName,
        brand,
        commType,
        templateManifest.version
      )
      Scanamo.exec(db)(templateSummaryTable.put(templateSummary))
      Logger.info(s"Written template summary to persistence $templateSummary")

      Right(())
    } else {
      Left(s"There is a newer version (${getTemplateSummary(templateManifest.id)
        .map(_.latestVersion)}) of comm (${templateManifest.id}) already, than being published (${templateManifest.version})")
    }
  }

  def listTemplateSummaries: List[TemplateSummary] = {
    val query = templateSummaryTable.scan()
    Scanamo.exec(db)(query).flatMap { result =>
      logIfError(result).toOption
    }
  }

  def getTemplateSummary(templateId: String): Option[TemplateSummary] = {
    println(s"TemplateId: $templateId")
    println(s"Summaries: ${listTemplateSummaries}")
    listTemplateSummaries
      .find(templateSummary => templateSummary.templateId == templateId)
  }

  // FIXME this does not work in the real life as the version is not indexed.
  def getTemplateVersion(templateId: String, version: String): Option[TemplateVersion] = {
    val query = templateVersionTable.get('templateId -> templateId and 'version -> version)
    Scanamo.exec(db)(query).flatMap(result => logIfError(result).toOption)
  }

  private def logIfError[A](res: Either[DynamoReadError, A]) = {
    res.left.map { err =>
      Logger.warn(s"Dynamo query failed with error: ${describe(err)}")
      err
    }
  }

  private def isNewestVersion(templateManifest: TemplateManifest): Boolean = {
    getTemplateSummary(templateManifest.id)
      .map(summary => TemplateSummary.versionCompare(templateManifest.version.trim, summary.latestVersion.trim))
      .forall {
        case Right(comparison) => if (comparison > 0) true else false
        case Left(error) =>
          Logger.warn("Unable to check if template is latest version: $error")
          false
      }
  }
}
