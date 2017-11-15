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
      "<html><head></head><body><img src=\"assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>".getBytes,
      Email,
      HtmlBody,
      None
    )
    val subject = UploadedTemplateFile(
      "email/subject.txt",
      "fsfdsfs<img src=\"assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">".getBytes,
      Email,
      Subject,
      None
    )
    val bodyText = UploadedTemplateFile("email/body.txt", "fsfdsfs".getBytes, Email, TextBody, None)
    val sender   = UploadedTemplateFile("email/sender.txt", "Test <testing@test.com>".getBytes, Email, Sender, None)
    val asset1   = UploadedTemplateFile("email/assets/image.png", "fsfdsfs".getBytes, Email, Asset, None)
    val asset2   = UploadedTemplateFile("email/assets/something/image.png", "fsfdsfs".getBytes, Email, Asset, None)

    val processedBodyHtml = UploadedTemplateFile(
      "email/body.html",
      "<html><head></head><body><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>".getBytes,
      Email,
      HtmlBody,
      None
    )
    val processedSubject = UploadedTemplateFile(
      "email/subject.txt",
      "fsfdsfs<img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">".getBytes,
      Email,
      Subject,
      None
    )

    val uploadedFiles  = List(bodyHtml, subject, bodyText, sender, asset1, asset2)
    val processedFiles = AssetProcessing.processAssets(region, s3Bucket, manifest, uploadedFiles).right.get

    processedFiles.assetFiles.size shouldBe 2
    processedFiles.assetFiles should contain allOf (asset1, asset2)
    processedFiles.templateFiles.size shouldBe 4
    processedFiles.templateFiles.map(file => (file.path, new String(file.contents))) should contain allOf (
      (processedBodyHtml.path, new String(processedBodyHtml.contents)),
      (processedSubject.path, new String(processedSubject.contents)),
      (bodyText.path, new String(bodyText.contents)),
      (sender.path, new String(sender.contents))
    )
  }

  it should "process uploaded SMS file" in {
    val smsTextBody = UploadedTemplateFile("sms/body.txt", "some message {{someVar}}".getBytes, SMS, TextBody, None)

    val uploadedFiles  = List(smsTextBody)
    val processedFiles = AssetProcessing.processAssets(region, s3Bucket, manifest, uploadedFiles).right.get
    processedFiles.assetFiles.size shouldBe 0
    processedFiles.templateFiles.size shouldBe 1
    processedFiles.templateFiles.map(file => (file.path, new String(file.contents))) should contain(
      smsTextBody.path,
      new String(smsTextBody.contents))
  }

  it should "process multiple channels" in {
    val emailBodyHtml = UploadedTemplateFile(
      "email/body.html",
      "<html><head></head><body><img src=\"assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>".getBytes,
      Email,
      HtmlBody,
      None
    )
    val emailSubject = UploadedTemplateFile(
      "email/subject.txt",
      "fsfdsfs<img src=\"assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">".getBytes,
      Email,
      Subject,
      None
    )
    val emailAsset  = UploadedTemplateFile("email/assets/image.png", "fsfdsfs".getBytes, Email, Asset, None)
    val smsTextBody = UploadedTemplateFile("sms/body.txt", "some message {{someVar}}".getBytes, SMS, TextBody, None)

    val uploadedFiles  = List(emailBodyHtml, emailSubject, emailAsset, smsTextBody)
    val processedFiles = AssetProcessing.processAssets(region, s3Bucket, manifest, uploadedFiles).right.get

    val expProcessedBodyHtml = UploadedTemplateFile(
      "email/body.html",
      "<html><head></head><body><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>".getBytes,
      Email,
      HtmlBody,
      None
    )
    val expProcessedSubject = UploadedTemplateFile(
      "email/subject.txt",
      "fsfdsfs<img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">".getBytes,
      Email,
      Subject,
      None
    )

    processedFiles.assetFiles.size shouldBe 1
    processedFiles.templateFiles.size shouldBe 3
    processedFiles.templateFiles.map(file => (file.path, new String(file.contents))) should contain allOf (
      (expProcessedBodyHtml.path, new String(expProcessedBodyHtml.contents)),
      (expProcessedSubject.path, new String(expProcessedSubject.contents)),
      (smsTextBody.path, new String(smsTextBody.contents))
    )
  }

}
