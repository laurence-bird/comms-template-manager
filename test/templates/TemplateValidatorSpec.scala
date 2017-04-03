package templates

import cats.data.NonEmptyList
import com.ovoenergy.comms.model.{CommManifest, CommType}
import org.scalatest.{FlatSpec, Matchers}
import com.ovoenergy.comms.templates.s3.S3Client

class TemplateValidatorSpec extends FlatSpec with Matchers {

  def generateUploadedFile(path: String, contents: String) = UploadedFile(path, contents.getBytes)

  object S3ClientStub extends S3Client {
    override def getUTF8TextFileContent(key: String): Option[String] = fail("Not expected to be invoked")
    override def listFiles(prefix: String): Seq[String]              = fail("Not expected to be invoked")
  }

  object S3ClientStubWithPartial extends S3Client {
    override def getUTF8TextFileContent(key: String): Option[String] = {
      if (key == "service/fragments/email/html/aValidPartial.html") Some("partial contents")
      else None
    }
    override def listFiles(prefix: String): Seq[String] = fail("Not expected to be invoked")
  }

  val commManifest = CommManifest(CommType.Service, "canary", "snapshot")

  behavior of "TemplateValidator"

  it should "error if extra files present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("email/extra.txt", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles) shouldBe Left(
      NonEmptyList.of("email/extra.txt is not an expected template file"))
  }

  it should "error if expected files not present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/body.txt", "fsfdsfs"),
      generateUploadedFile("email/sender.txt", "Test <testing@test.com>")
    )
    TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles) shouldBe Left(
      NonEmptyList.of("No subject file has been provided in template"))
  }

  it should "not error if zip contains extra root folder" in {
    val uploadedFiles = List(
      generateUploadedFile("template/email/body.html", "fsfdsfs"),
      generateUploadedFile("template/email/subject.txt", "fsfdsfs")
    )
    val result = TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles).left.get.toList
    result should contain("No html body file has been provided in template")
    result should contain("No subject file has been provided in template")
  }

  it should "merge multiple errors" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/extra.txt", "fsdfsdf")
    )
    val result = TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles).left.get.toList
    result should contain("email/extra.txt is not an expected template file")
    result should contain("No subject file has been provided in template")
  }

  it should "not error if full fileset present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs"),
      generateUploadedFile("email/body.txt", "fsfdsfs"),
      generateUploadedFile("email/sender.txt", "Test <testing@test.com>"),
      generateUploadedFile("email/assets/image.png", "fsfdsfs"),
      generateUploadedFile("email/assets/something/image.png", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles) shouldBe Right(())
  }

  it should "not error if minimal fileset present" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "fsfdsfs"),
      generateUploadedFile("email/subject.txt", "fsfdsfs")
    )
    TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles) shouldBe Right(())
  }

  it should "not error if valid partial referenced" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "{{> aValidPartial}}"),
      generateUploadedFile("email/subject.txt", "{{something.else}}")
    )
    TemplateValidator.validateTemplate(S3ClientStubWithPartial, commManifest, uploadedFiles) shouldBe Right(())
  }

  it should "error if problem with required template data" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "{{something}}"),
      generateUploadedFile("email/subject.txt", "{{something.else}}")
    )
    val result = TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles).left.get.toList
    result should contain("something is referenced as both a mandatory object and a mandatory string}")
  }

  it should "error if non-existent partial referenced" in {
    val uploadedFiles = List(
      generateUploadedFile("email/body.html", "{{> anInvalidPartial}}"),
      generateUploadedFile("email/subject.txt", "{{something.else}}")
    )
    val result =
      TemplateValidator.validateTemplate(S3ClientStubWithPartial, commManifest, uploadedFiles).left.get.toList
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
    val result = TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles).left.get.toList
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
    TemplateValidator.validateTemplate(S3ClientStub, commManifest, uploadedFiles) shouldBe Right(())
  }
}
