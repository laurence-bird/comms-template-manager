package logic

import java.time.Instant

import cats._
import com.ovoenergy.comms.model.{CommManifest, CommType}
import models.{TemplateVersion, ZippedRawTemplate}
import org.scalatest.{FlatSpec, Matchers}

class TemplateOpSpec extends FlatSpec with Matchers {

  val templateFiles = Map(
    "emailBody" -> "My email body".getBytes,
    "header"    -> "My header".getBytes,
    "textBody"  -> "My text body".getBytes
  )

  val publishedAt = Instant.now()

  def genTemplateVersion(commManifest: CommManifest) =
    TemplateVersion(
      commManifest.name,
      commManifest.version,
      publishedAt,
      "Mr Test",
      commManifest.commType
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

    val commManifest = CommManifest(CommType.Service, "testTemplate", "1.0")

    val template       = TemplateOp.retrieveTemplate(commManifest).foldMap(testInterpreter)
    val expectedResult = ZippedRawTemplate(templateFileStream)

    template shouldBe expectedResult
  }

}
