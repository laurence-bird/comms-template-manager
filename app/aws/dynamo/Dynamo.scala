package aws.dynamo

import cats.syntax.either._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.scanamo._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.error.DynamoReadError._
import com.gu.scanamo.syntax._
import models.{TemplateSummary, TemplateVersion}
import play.api.Logger


class Dynamo(db: AmazonDynamoDB, templateVersionTable: Table[TemplateVersion], templateSummaryTable: Table[TemplateSummary]) {

  def listVersions(commName: String): Seq[TemplateVersion] = {
    val query = templateVersionTable.query('commName -> commName)
    Scanamo.exec(db)(query).flatMap{ result =>
      logIfError(result).toOption
    }
  }

  def listTemplates: List[TemplateSummary] = {
    val query = templateSummaryTable.scan()
    Scanamo.exec(db)(query).flatMap{ result =>
      logIfError(result).toOption
    }
  }

  def getTemplateVersion(commName: String, version: String): Option[TemplateVersion] = {
    val query = templateVersionTable.get('commName -> commName and 'version -> version)
    Scanamo.exec(db)(query).flatMap(result => logIfError(result).toOption)
  }

  private def logIfError[A](res: Either[DynamoReadError, A]) = {
    res.left.map{
      err => Logger.warn(s"Dynamo query failed with error: ${describe(err)}")
        err
    }
  }
}
