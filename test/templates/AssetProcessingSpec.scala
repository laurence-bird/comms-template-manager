package templates

import com.amazonaws.regions.Regions
import com.ovoenergy.comms.model.{Email, SMS}
import com.ovoenergy.comms.model.{Channel, CommManifest, Service}
import org.scalatest.{FlatSpec, Matchers}

class AssetProcessingSpec extends FlatSpec with Matchers {

  val s3Bucket = "dev-ovo-comms-template-assets"
  val manifest = CommManifest(Service, "commName", "1.0")
  val region   = Regions.EU_WEST_1

  behavior of "Template AssetProcessing"

  it should "process uploaded email files" in {
    val bodyHtml = UploadedTemplateFile(
      "email/body.html",
      Content(
        "<html><head></head><body><img src=\"assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>"),
      Email,
      HtmlBody,
      None
    )
    val subject = UploadedTemplateFile(
      "email/subject.txt",
      Content("fsfdsfs<img src=\"assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">"),
      Email,
      Subject,
      None
    )
    val bodyText = UploadedTemplateFile("email/body.txt", Content("fsfdsfs"), Email, TextBody, None)
    val sender   = UploadedTemplateFile("email/sender.txt", Content("Test <testing@test.com>"), Email, Sender, None)
    val asset1   = UploadedTemplateFile("email/assets/image.png", Content("fsfdsfs"), Email, Asset, None)
    val asset2   = UploadedTemplateFile("email/assets/something/image.png", Content("fsfdsfs"), Email, Asset, None)

    val processedBodyHtml = UploadedTemplateFile(
      "email/body.html",
      Content(
        "<html><head></head><body><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>"),
      Email,
      HtmlBody,
      None
    )
    val processedSubject = UploadedTemplateFile(
      "email/subject.txt",
      Content(
        "fsfdsfs<img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">"),
      Email,
      Subject,
      None
    )

    val uploadedFiles  = List(bodyHtml, subject, bodyText, sender, asset1, asset2)
    val processedFiles = AssetProcessing.processAssets(region, s3Bucket, manifest, uploadedFiles).right.get

    processedFiles.assetFiles.size shouldBe 2
    processedFiles.assetFiles should contain allOf (asset1, asset2)
    processedFiles.templateFiles.size shouldBe 4
    processedFiles.templateFiles.map(file => (file.path, file.utf8Content)) should contain allOf (
      (processedBodyHtml.path, processedBodyHtml.utf8Content),
      (processedSubject.path, processedSubject.utf8Content),
      (bodyText.path, bodyText.utf8Content),
      (sender.path, sender.utf8Content)
    )
  }

  it should "process uploaded SMS file" in {
    val smsTextBody = UploadedTemplateFile("sms/body.txt", Content("some message {{someVar}}"), SMS, TextBody, None)

    val uploadedFiles  = List(smsTextBody)
    val processedFiles = AssetProcessing.processAssets(region, s3Bucket, manifest, uploadedFiles).right.get
    processedFiles.assetFiles.size shouldBe 0
    processedFiles.templateFiles.size shouldBe 1
    processedFiles.templateFiles.map(file => (file.path, file.utf8Content)) should contain(smsTextBody.path,
                                                                                           smsTextBody.utf8Content)
  }

  it should "process multiple channels" in {
    val emailBodyHtml = UploadedTemplateFile(
      "email/body.html",
      Content(
        "<html><head></head><body><img src=\"assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>"),
      Email,
      HtmlBody,
      None
    )
    val emailSubject = UploadedTemplateFile(
      "email/subject.txt",
      Content("fsfdsfs<img src=\"assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">"),
      Email,
      Subject,
      None
    )
    val emailAsset  = UploadedTemplateFile("email/assets/image.png", Content("fsfdsfs"), Email, Asset, None)
    val smsTextBody = UploadedTemplateFile("sms/body.txt", Content("some message {{someVar}}"), SMS, TextBody, None)

    val uploadedFiles  = List(emailBodyHtml, emailSubject, emailAsset, smsTextBody)
    val processedFiles = AssetProcessing.processAssets(region, s3Bucket, manifest, uploadedFiles).right.get

    val expProcessedBodyHtml = UploadedTemplateFile(
      "email/body.html",
      Content(
        "<html><head></head><body><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>"),
      Email,
      HtmlBody,
      None
    )
    val expProcessedSubject = UploadedTemplateFile(
      "email/subject.txt",
      Content(
        "fsfdsfs<img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">"),
      Email,
      Subject,
      None
    )

    processedFiles.assetFiles.size shouldBe 1
    processedFiles.templateFiles.size shouldBe 3
    processedFiles.templateFiles.map(file => (file.path, file.utf8Content)) should contain allOf (
      (expProcessedBodyHtml.path, expProcessedBodyHtml.utf8Content),
      (expProcessedSubject.path, expProcessedSubject.utf8Content),
      (smsTextBody.path, smsTextBody.utf8Content)
    )
  }

}
