package tableMigration

import aws.AwsContextProvider
import aws.dynamo.{Dynamo, DynamoTemplateId}
import com.amazonaws.regions.Regions
import com.gu.scanamo.{Scanamo, Table}
import models._
import aws.dynamo.DynamoFormats._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.gu.scanamo.error.DynamoReadError
import com.ovoenergy.comms.model.TemplateManifest
import com.ovoenergy.comms.templates.util.Hash
import cats.implicits._

import scala.collection.immutable

object TableMigrator {

  val (dynamoClient, s3Client) = AwsContextProvider.genContext(false, Regions.EU_WEST_1)

  val dynamo = new Dynamo(
    dynamoClient,
    Table[TemplateVersionLegacy]("template-manager-DEV-TemplateVersionTable-15FUF2VRQBG72"),
    Table[TemplateSummaryLegacy]("template-manager-DEV-TemplateSummaryTable-1EQQIGC8NAPIC")
  )

  val newTemplateSummary = Table[TemplateSummary]("")
  val newTemplateVersion = Table[TemplateVersion]("")

  val dynamoTemplateId = new DynamoTemplateId(
    dynamoClient,
    newTemplateVersion,
    newTemplateSummary
  )

  def createNewTemplateSummariesTable() = dynamo
    .listTemplateSummaries
    .map(legacy => TemplateSummary(Hash(legacy.commName), legacy.commName, getBrand(legacy.commName), legacy.commType, legacy.latestVersion))
    .map(saveTemplateSummary)
    .map(_.get.right.get)
    .map(a => createNewTemplateVersionTable(a.commName))

  def createNewTemplateVersionTable(commName: String) = dynamo
    .listVersions(commName)
    .map(legacy => TemplateVersion(Hash(legacy.commName), legacy.version, legacy.commName, legacy.commType, legacy.publishedAt, legacy.publishedBy, legacy.channels))

  def saveTemplateSummary(templateSummary: TemplateSummary) = Scanamo.exec(dynamoClient)(newTemplateSummary.put(templateSummary))
  def saveTemplateVersion(templateVersion: TemplateVersion) = Scanamo.exec(dynamoClient)(newTemplateVersion.put(templateVersion))

  def getBrand(commName: String): Brand = ???

}
