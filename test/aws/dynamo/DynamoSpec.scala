package aws.dynamo

import java.time.Instant

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import util.LocalDynamoDB
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.{Scanamo, Table}
import com.ovoenergy.comms.model.CommType._
import aws.dynamo.DynamoFormats._
import com.ovoenergy.comms.model.{CommManifest, CommType}
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
      TemplateVersion("comm1", "1.0", Instant.now, "laurence", Service),
      TemplateVersion("comm2", "1.0", Instant.now, "laurence", Service),
      TemplateVersion("comm2", "2.0", Instant.now, "chris", Service)
    )

    val templateSummarries = Seq(
      TemplateSummary("comm1", Service, "1.0"),
      TemplateSummary("comm2", Service, "2.0")
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
    result.map(_.version) should contain allOf ("1.0", "2.0")
    result.map(_.publishedBy) should contain allOf ("chris", "laurence")
  }

  it should "return empty list if comm not in the database" in {
    val result = dynamo.listVersions("comm99")

    result.length shouldBe 0
  }

  it should "list all template summaries in the database" in {
    val result = dynamo.listTemplateSummaries
    result.length shouldBe 2
    result should contain allOf (
      TemplateSummary("comm1", Service, "1.0"),
      TemplateSummary("comm2", Service, "2.0")
    )
  }

  it should "retrieve a specific template version" in {
    val result = dynamo.getTemplateVersion("comm2", "2.0")
    result.map(_.publishedBy) shouldBe Some("chris")
  }

  it should "return a None if a template version doesn't exist" in{
    val result = dynamo.getTemplateVersion("comm2", "yolo")
    result shouldBe None
  }

  it should "error when writing a new version that is not the newest" in {
    dynamo.writeNewVersion(CommManifest(CommType.Service, "comm2", "1.5")).left.get shouldBe "There is a newer version (Some(2.0)) of comm (comm2) already, than being published (1.5)"
  }

  it should "write new version" in {
    dynamo.writeNewVersion(CommManifest(CommType.Service, "comm2", "2.5")) shouldBe Right(())
    val summaries = dynamo.listTemplateSummaries
    summaries should contain(TemplateSummary("comm2", Service, "2.5"))

    val versions = dynamo.listVersions("comm2")
    versions.length shouldBe 3
    versions.head.commName shouldBe "comm2"
    versions.map(_.version) should contain allOf ("1.0", "2.0", "2.5")
    versions.map(_.publishedBy) should contain allOf ("chris", "laurence", "CommsTemplateManager")
  }
}
