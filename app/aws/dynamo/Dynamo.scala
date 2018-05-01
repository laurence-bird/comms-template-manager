package aws.dynamo

import cats.syntax.either._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.scanamo._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.error.DynamoReadError._
import com.gu.scanamo.syntax._
import com.ovoenergy.comms.model.{Channel, CommManifest, CommType}
import models.{TemplateSummary, TemplateVersion}
import play.api.Logger

class Dynamo(db: AmazonDynamoDB,
             templateVersionTable: Table[TemplateVersion],
             templateSummaryTable: Table[TemplateSummary]) {

  def listVersions(commName: String): Seq[TemplateVersion] = {
    val query = templateVersionTable.query('commName -> commName)
    Scanamo.exec(db)(query).flatMap { result =>
      logIfError(result).toOption
    }
  }

  //TODO - Not a conditional write as desiredn
  def writeNewVersion(commManifest: CommManifest, publishedBy: String, channels: List[Channel]): Either[String, Unit] = {

    if (isNewestVersion(commManifest)) {
      val templateVersion = TemplateVersion(
        commManifest = commManifest,
        publishedBy = publishedBy,
        channels = channels
      )
      Scanamo.exec(db)(templateVersionTable.put(templateVersion))
      Logger.info(s"Written template version to persistence $templateVersion")

      val templateSummary = TemplateSummary(
        commManifest = commManifest
      )
      Scanamo.exec(db)(templateSummaryTable.put(templateSummary))
      Logger.info(s"Written template summary to persistence $templateSummary")

      Right(())
    } else {
      Left(s"There is a newer version (${getTemplateSummary(commManifest.name)
        .map(_.latestVersion)}) of comm (${commManifest.name}) already, than being published (${commManifest.version})")
    }
  }

  def listTemplateSummaries: List[TemplateSummary] = {
    val query = templateSummaryTable.scan()
    Scanamo.exec(db)(query).flatMap { result =>
      logIfError(result).toOption
    }
  }

  def getTemplateSummary(commName: String): Option[TemplateSummary] = {
    listTemplateSummaries
      .find(templateSummary => templateSummary.commName == commName)
  }

  // FIXME this does not work in the real life as the version is not indexed.
  def getTemplateVersion(commName: String, version: String): Option[TemplateVersion] = {
    val query = templateVersionTable.get('commName -> commName and 'version -> version)
    Scanamo.exec(db)(query).flatMap(result => logIfError(result).toOption)
  }

  private def logIfError[A](res: Either[DynamoReadError, A]) = {
    res.left.map { err =>
      Logger.warn(s"Dynamo query failed with error: ${describe(err)}")
      err
    }
  }

  private def isNewestVersion(commManifest: CommManifest): Boolean = {
    getTemplateSummary(commManifest.name)
      .map(summary => TemplateSummary.versionCompare(commManifest.version.trim, summary.latestVersion.trim))
      .forall {
        case Right(comparison) => if (comparison > 0) true else false
        case Left(error) =>
          Logger.warn("Unable to check if template is latest version: $error")
          false
      }
  }
}
