package aws.dynamo

import java.time.Instant

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import util.LocalDynamoDB
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.{Scanamo, Table}
import com.ovoenergy.comms.model.CommType._
import aws.dynamo.DynamoFormats._
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates.util.Hash
import models.Brand.Ovo
import models.{TemplateSummary, TemplateVersion}

class DynamoSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val client                = LocalDynamoDB.client()
  val templateVersionsTable = "template-version"
  val templateSummaryTable  = "template-summary"
  val publishedBy           = "joe.bloggs"

  val dynamo = new Dynamo(
    client,
    Table[TemplateVersion](templateVersionsTable),
    Table[TemplateSummary](templateSummaryTable)
  )

//  case class LegacyTemplateVersion(commName: String,
//                                   version: String,
//                                   publishedAt: Instant,
//                                   publishedBy: String,
//                                   commType: CommType)

  val templateVersions = Seq(
    TemplateVersion(Hash("comm1"), "1.0", "comm1", Service, Instant.now, "laurence", Some(Nil)),
    TemplateVersion(Hash("comm2"), "1.0", "comm2", Service, Instant.now, "laurence", Some(Nil)),
    TemplateVersion(Hash("comm2"), "2.0", "comm2", Service, Instant.now, "chris", Some(Nil))
  )

//  val legacyTemplateVersions = Seq(
//    LegacyTemplateVersion("legacyComm1", "1.0", Instant.now, "laurence", Service),
//    LegacyTemplateVersion("legacyComm2", "1.0", Instant.now, "laurence", Service),
//    LegacyTemplateVersion("legacyComm2", "2.0", Instant.now, "chris", Service)
//  )

  val templateSummaries = Seq(
    TemplateSummary(Hash("comm1"), "comm1", Service, "1.0"),
    TemplateSummary(Hash("comm2"), "comm2", Service, "2.0"),
    TemplateSummary(Hash("legacyComm1"), "legacyComm1", Service, "1.0"),
    TemplateSummary(Hash("legacyComm2"), "legacyComm2", Service, "2.0")
  )

  override def beforeAll(): Unit = {
    LocalDynamoDB.createTable(client)(templateVersionsTable)('templateId -> S, 'version -> S)
    LocalDynamoDB.createTable(client)(templateSummaryTable)('templateId  -> S)

    templateSummaries.foreach { ts =>
      Scanamo.put(client)(templateSummaryTable)(ts)
    }

    templateVersions.foreach { t =>
      Scanamo.put(client)(templateVersionsTable)(t)
    }

//    legacyTemplateVersions.foreach { t =>
//      Scanamo.put(client)(templateVersionsTable)(t)
//    }
  }

  override def afterAll(): Unit = {
    client.deleteTable(templateVersionsTable)
    client.deleteTable(templateSummaryTable)
  }

  it should "list all version of a comm in the database" in {
    val result = dynamo.listVersions(Hash("comm2"))

    result.length shouldBe 2
    result.head.commName shouldBe "comm2"
    result.map(_.version) should contain allOf ("1.0", "2.0")
    result.map(_.publishedBy) should contain allOf ("chris", "laurence")
  }

  it should "return empty list if comm not in the database" in {
    val result = dynamo.listVersions(Hash("comm99"))

    result.length shouldBe 0
  }

  it should "list all template summaries in the database" in {
    val result = dynamo.listTemplateSummaries
    result.length shouldBe 4
    result should contain theSameElementsAs templateSummaries
  }

  it should "retrieve a specific template version" in {
    val result = dynamo.getTemplateVersion(Hash("comm2"), "2.0")
    result.map(_.publishedBy) shouldBe Some("chris")
  }

//  it should "retrieve a legacy template version" in {
//    val result = dynamo.getTemplateVersion(Hash("legacyComm2"), "2.0")
//    result.map(_.publishedBy) shouldBe Some("chris")
//  }

  it should "return a None if a template version doesn't exist" in {
    val result = dynamo.getTemplateVersion(Hash("comm2"), "yolo")
    result shouldBe None
  }

  it should "error when writing a new version that is not the newest" in {
    dynamo
      .writeNewVersion(TemplateManifest(Hash("comm2"), "1.5"), "comm2", Service, publishedBy, Nil)
      .left
      .get shouldBe s"There is a newer version (Some(2.0)) of comm (${Hash("comm2")}) already, than being published (1.5)"
  }

  it should "error when writing a new version that already exists" in {
    dynamo
      .writeNewVersion(TemplateManifest(Hash("comm2"), "2.0"), "comm2", Service, publishedBy, Nil)
      .left
      .get shouldBe s"There is a newer version (Some(2.0)) of comm (${Hash("comm2")}) already, than being published (2.0)"
  }

  it should "write new version" in {
    dynamo.writeNewVersion(TemplateManifest(Hash("comm2"), "2.5"), "comm2", Service, publishedBy, Nil) shouldBe Right(
      ())
    val summaries = dynamo.listTemplateSummaries
    summaries should contain(TemplateSummary(Hash("comm2"), "comm2", Service, "2.5"))

    val versions = dynamo.listVersions(Hash("comm2"))
    versions.length shouldBe 3
    versions.head.commName shouldBe "comm2"
    versions.map(_.version) should contain allOf ("1.0", "2.0", "2.5")
    versions.map(_.publishedBy) should contain allOf ("chris", "laurence", publishedBy)
  }
}
