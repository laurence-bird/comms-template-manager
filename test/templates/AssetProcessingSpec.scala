package templates

import com.amazonaws.regions.Regions
import com.ovoenergy.comms.model.CommManifest
import com.ovoenergy.comms.model.CommType.Service
import org.scalatest.{FlatSpec, Matchers}

class AssetProcessingSpec extends FlatSpec with Matchers {

  def generateUploadedFile(path: String, contents: String) = UploadedFile(path, contents.getBytes)

  val s3Bucket = "dev-ovo-comms-template-assets"
  val manifest = CommManifest(Service, "commName", "1.0")
  val region   = Regions.EU_WEST_1

  behavior of "Template AssetProcessing"

  it should "process uploaded email files" in {
    val bodyHtml = generateUploadedFile(
      "email/body.html",
      "<html><head></head><body><img src=\"assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>"
    )
    val subject = generateUploadedFile(
      "email/subject.txt",
      "fsfdsfs<img src=\"assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">")
    val bodyText = generateUploadedFile("email/body.txt", "fsfdsfs")
    val sender   = generateUploadedFile("email/sender.txt", "Test <testing@test.com>")
    val asset1   = generateUploadedFile("email/assets/image.png", "fsfdsfs")
    val asset2   = generateUploadedFile("email/assets/something/image.png", "fsfdsfs")

    val processedBodyHtml = generateUploadedFile(
      "email/body.html",
      "<html><head></head><body><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"><img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/assets/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\"></body></html>"
    )
    val processedSubject = generateUploadedFile(
      "email/subject.txt",
      "fsfdsfs<img src=\"https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/commName/1.0/email/assets/something/image.png\" alt=\"Smiley face\" height=\"42\" width=\"42\">"
    )

    val uploadedFiles  = List(bodyHtml, subject, bodyText, sender, asset1, asset2)
    val processedFiles = AssetProcessing.processAssets(region, s3Bucket, manifest, uploadedFiles).right.get

    processedFiles.assetFiles.size shouldBe 2
    processedFiles.assetFiles should contain allOf (asset1, asset2)
    processedFiles.templateFiles.size shouldBe 4
    processedFiles.templateFiles.map(file => (file.path, new String(file.contents))) should contain allOf
      ((processedBodyHtml.path, new String(processedBodyHtml.contents)), (processedSubject.path,
                                                                          new String(processedSubject.contents)), (bodyText.path,
                                                                                                                   new String(
                                                                                                                     bodyText.contents)), (sender.path,
                                                                                                                                           new String(
                                                                                                                                             sender.contents)))
  }

}
