package logic

import java.time.Instant

import cats._
import com.ovoenergy.comms.model._
import models.{TemplateVersion, ZippedRawTemplate}
import org.scalatest.{FlatSpec, Matchers}
import com.ovoenergy.comms.templates.util.Hash

class TemplateOpSpec extends FlatSpec with Matchers {

  val commName = "testTemplate"

  val commType = Service

  val templateFiles = Map(
    "emailBody" -> "My email body".getBytes,
    "header"    -> "My header".getBytes,
    "textBody"  -> "My text body".getBytes
  )

  val publishedAt = Instant.now()

  def genTemplateVersion(templateManifest: TemplateManifest) =
    TemplateVersion(
      templateManifest,
      commName,
      commType,
      "Mr Test",
      List[Channel]()
    )

  val templateFileStream = "testing-is-fun".getBytes()

  val testInterpreter: TemplateOpA ~> Id = new (TemplateOpA ~> Id) {
    override def apply[A](fa: TemplateOpA[A]): Id[A] = fa match {
      case RetrieveTemplateFromS3(commManifest)            => templateFiles
      case RetrieveTemplateVersionFromDynamo(commManifest) => genTemplateVersion(commManifest)
      case CompressTemplates(templatesFiles)               => templateFileStream
      case _                                               => ???
    }
  }

  it should "Retrieve a template" in {

    val templateManifest = TemplateManifest(Hash(commName), "1.0")

    val template       = TemplateOp.retrieveTemplate(templateManifest).foldMap(testInterpreter)
    val expectedResult = ZippedRawTemplate(templateFileStream)

    template shouldBe expectedResult
  }

}
