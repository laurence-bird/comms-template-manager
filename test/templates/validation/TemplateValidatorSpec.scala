package templates.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates.s3.S3Client
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.MimeTypes
import templates._

class TemplateValidatorSpec extends FlatSpec with Matchers {

  def generateUploadedFile(path: String, contents: String) = UploadedFile(path, Content(contents))

  object S3ClientStub extends S3Client {
    override def getUTF8TextFileContent(key: String): Option[String] = fail("Not expected to be invoked")

    override def listFiles(prefix: String): Seq[String] = fail("Not expected to be invoked")
  }

  object S3ClientStubWithPartial extends S3Client {
    override def getUTF8TextFileContent(key: String): Option[String] = {
      if (key == "service/fragments/email/html/aValidPartial.html") Some("partial contents")
      else None
    }

    override def listFiles(prefix: String): Seq[String] = fail("Not expected to be invoked")
  }

  val happyPrintTemplateValidator = (templates: List[UploadedTemplateFile]) => Valid(templates)

  val commManifest = CommManifest(Service, "canary", "snapshot")

  behavior of "TemplateValidator"

  it should "error if extra files present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("email/extra.txt", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles) shouldBe Left(
      NonEmptyList.of("email/extra.txt is not an expected template file"))
  }

  it should "error if expected files not present in email template" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/body.txt", "fsfdsfs"),
      generateUploadedFile("email/sender.txt", "Test <testing@test.com>")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles) shouldBe Left(
      NonEmptyList.of("No email subject file has been provided in template"))
  }

  it should "error if zip contains extra root folder" in {
    val uploadedFiles = List(
      generateUploadedFile("template/email/body.html", "fsfdsfs"),
      generateUploadedFile("template/email/subject.txt", "fsfdsfs")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .left
      .get
      .toList
    result should contain("template/email/body.html is not an expected template file")
    result should contain("template/email/subject.txt is not an expected template file")
    result should contain("Template has no channels defined")
  }

  it should "merge multiple errors" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/extra.txt", "fsdfsdf")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .left
      .get
      .toList
    result should contain("email/extra.txt is not an expected template file")
    result should contain("No email subject file has been provided in template")
  }

  it should "not error if full fileset present for email template" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("email/body.txt", "fsfdsfs"),
      generateUploadedFile("email/sender.txt", "Test <testing@test.com>"),
      generateUploadedFile("email/assets/image.png", "fsfdsfs"),
      generateUploadedFile("email/assets/something/image.png", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles) shouldBe 'right
  }

  it should "not error if minimal fileset present for email template" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles) shouldBe 'right
  }

  it should "not error if valid partial referenced" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "{{> aValidPartial}}"),
      generateUploadedFile("email/subject.txt", "{{something.else}}")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStubWithPartial,
                                                                    commManifest,
                                                                    uploadedFiles) shouldBe 'right
  }

  it should "error if problem with required template data" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "{{something}}"),
      generateUploadedFile("email/subject.txt", "{{something.else}}")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .left
      .get
      .toList
    result should contain("something is referenced as both a mandatory object and a mandatory string}")
  }

  it should "error if non-existent partial referenced" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "{{> anInvalidPartial}}"),
      generateUploadedFile("email/subject.txt", "{{something.else}}")
    )
    val result =
      TemplateValidator
        .validateTemplate(happyPrintTemplateValidator)(S3ClientStubWithPartial, commManifest, uploadedFiles)
        .left
        .get
        .toList
    result should contain("Could not find shared partial: anInvalidPartial")
  }

  it should "error if non-existent assets are referenced" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html",
                           "<img src=\"assets/smiley.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\">"),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("email/assets/image.gif", "fsfdsfs"),
      generateUploadedFile("email/assets/something/image.png", "fsfdsfs")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .left
      .get
      .toList
    result should contain(
      "The file email/body.html contains the reference 'assets/smiley.gif' to a non-existent asset file")

  }

  it should "not error if valid assets are referenced" in {
    val uploadedFiles = List(
      generateUploadedFile(
        "email/body.html",
        "<img src=\"assets/smiley.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/another.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\">"
      ),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("email/assets/smiley.gif", "fsfdsfs"),
      generateUploadedFile("email/assets/something/another.gif", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles) shouldBe 'right
  }

  it should "error if no channel templates are present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/assets/image.gif", "fsfdsfs")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .left
      .get
      .toList
    result should contain("Template has no channels defined")
  }

  it should "not error if only SMS template is present" in {
    val uploadedFiles = List(
      generateUploadedFile("sms/body.txt", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles) shouldBe 'right
  }

  it should "not error if valid email and SMS channel templates are present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("sms/body.txt", "fsfdsfs")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .right
      .get
    for (i <- 0 to 2) {
      result(i).path shouldBe uploadedFiles(i).path
      result(i).utf8Content shouldBe uploadedFiles(i).utf8Content
    }
    result(0).channel shouldBe Email
    result(0).fileType shouldBe HtmlBody
    result(1).channel shouldBe Email
    result(1).fileType shouldBe Subject
    result(2).channel shouldBe SMS
    result(2).fileType shouldBe TextBody
  }

  it should "not error if valid email, print and SMS channel templates are present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("sms/body.txt", "fsfdsfs"),
      generateUploadedFile("print/body.html", "blablabla")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .right
      .get
    for (i <- 0 to 3) {
      result(i).path shouldBe uploadedFiles(i).path
      result(i).utf8Content shouldBe uploadedFiles(i).utf8Content
    }
    result(0).channel shouldBe Email
    result(0).fileType shouldBe HtmlBody
    result(1).channel shouldBe Email
    result(1).fileType shouldBe Subject
    result(2).channel shouldBe SMS
    result(2).fileType shouldBe TextBody
    result(3).channel shouldBe Print
    result(3).fileType shouldBe HtmlBody
  }

  it should "not error if valid print body referenced" in {
    val uploadedFiles = List(
      generateUploadedFile("print/body.html", "Print template body")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStubWithPartial,
                                                                    commManifest,
                                                                    uploadedFiles) shouldBe 'right
  }

  it should "not error if valid print assets are referenced" in {
    val uploadedFiles = List(
      generateUploadedFile(
        "print/body.html",
        "<img src=\"assets/smiley.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/another.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\">"
      ),
      generateUploadedFile("print/assets/smiley.gif", "fsfdsfs"),
      generateUploadedFile("print/assets/something/another.gif", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles) shouldBe 'right
  }

  it should "error if non-existent print assets are referenced" in {
    val uploadedFiles = List(
      generateUploadedFile("print/body.html",
                           "<img src=\"assets/smiley.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\">"),
      generateUploadedFile("print/assets/image.gif", "fsfdsfs"),
      generateUploadedFile("print/assets/something/image.png", "fsfdsfs")
    )
    val result = TemplateValidator
      .validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .left
      .get
      .toList
    result should contain(
      "The file print/body.html contains the reference 'assets/smiley.gif' to a non-existent asset file")

  }

  it should "propagate errors from printTemplateValidator" in {
    val unhappyPrintTemplateValidator =
      (templates: List[UploadedTemplateFile]) => Invalid(NonEmptyList.of("Something went wrong", "very bad", "oh no"))
    val uploadedFiles = List(
      generateUploadedFile("print/body.html",
                           "<img src=\"assets/smiley.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\">"),
      generateUploadedFile("print/assets/image.gif", "fsfdsfs"),
      generateUploadedFile("print/assets/something/image.png", "fsfdsfs")
    )
    val result = TemplateValidator
      .validateTemplate(unhappyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)
      .left
      .get
      .toList
    result should contain theSameElementsAs List(
      "The file print/body.html contains the reference 'assets/smiley.gif' to a non-existent asset file",
      "Something went wrong",
      "very bad",
      "oh no"
    )
  }

  it should "determine file mime types" in {
    val uploadedFiles = List(
      generateUploadedFile(
        "email/body.html",
        "<img src=\"assets/smiley.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/another.gif\" alt=\"Smiley face\" height=\"42\" width=\"42\">"
      ),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("email/assets/smiley.gif", "fsfdsfs"),
      generateUploadedFile("email/assets/something/another.gif", "fsfdsfs"),
      generateUploadedFile("print/assets/something/anotherAnother.tiff", "yo")
    )

    val result =
      TemplateValidator.validateTemplate(happyPrintTemplateValidator)(S3ClientStub, commManifest, uploadedFiles)

    result.right.get.flatMap(_.contentType) should contain theSameElementsAs Seq("text/html",
                                                                                 "text/plain",
                                                                                 "image/gif",
                                                                                 "image/gif",
                                                                                 "image/tiff")
  }
}
