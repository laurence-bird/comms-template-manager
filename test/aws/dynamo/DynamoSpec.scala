package aws.dynamo

import java.time.Instant

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import util.LocalDynamoDB
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.{Scanamo, Table}
import com.ovoenergy.comms.model.CommType._
import aws.dynamo.DynamoFormats._
import models.{TemplateSummary, TemplateVersion}

class DynamoSpec extends FlatSpec
  with Matchers
  with BeforeAndAfterAll {

  val client = LocalDynamoDB.client()
  val templateVersionsTable = "template-version"
  val templateSummaryTable = "template-summary"

  val dynamo = new Dynamo(
    client,
    Table[TemplateVersion](templateVersionsTable),
    Table[TemplateSummary](templateSummaryTable)
  )

  override def beforeAll(): Unit = {
    LocalDynamoDB.createTable(client)(templateVersionsTable)('commName -> S, 'version -> S)
    LocalDynamoDB.createTable(client)("template-summary")('commName -> S)

    val templateVersions = Seq(
      TemplateVersion("comm1", "version1", Instant.now, "laurence", Service),
      TemplateVersion("comm2", "version1", Instant.now, "laurence", Service),
      TemplateVersion("comm2", "version2", Instant.now, "chris", Service)
    )

    val templateSummarries = Seq(
      TemplateSummary("comm1", Service, "version1"),
      TemplateSummary("comm2", Service, "version2")
    )

    templateSummarries.foreach{ ts =>
      Scanamo.put(client)(templateSummaryTable)(ts)
    }

    templateVersions.foreach{ t =>
      Scanamo.put(client)(templateVersionsTable)(t)
    }
  }

  override def afterAll(): Unit = {
    client.deleteTable(templateVersionsTable)
    client.deleteTable(templateSummaryTable)
  }

  it should "list all version of a comm in the database" in {
    val result = dynamo.listVersions("comm2")

    result.length shouldBe 2
    result.head.commName shouldBe "comm2"
    result.map(_.version) should contain allOf ("version1", "version2")
    result.map(_.publishedBy) should contain allOf ("chris", "laurence")
  }

  it should "list all template summaries in the database" in {
    val result = dynamo.listTemplates
    result.length shouldBe 2
    result should contain allOf (
      TemplateSummary("comm1", Service, "version1"),
      TemplateSummary("comm2", Service, "version2")
    )
  }

  it should "retrieve a specific template version" in {
    val result = dynamo.getTemplateVersion("comm2", "version2")
    result.map(_.publishedBy) shouldBe Some("chris")
  }

  it should "return a None if a template version doesn't exist" in{
    val result = dynamo.getTemplateVersion("comm2", "yolo")
    result shouldBe None
  }
}
