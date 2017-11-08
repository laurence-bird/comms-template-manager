package templates

import cats.data.NonEmptyList
import com.amazonaws.regions.Regions
import com.ovoenergy.comms.model.Print
import org.scalatest.{FlatSpec, Matchers}
import templates.AssetProcessing.ProcessedFiles
import templates.validation.HtmlContentParser

class InjectorSpec extends FlatSpec with Matchers {

  val assetsBucket = "ovo-comms-template-assets"
  val awsContext   = aws.Context(null, null, null, null, null, assetsBucket, Regions.EU_WEST_1)

  it should "Insert default print stylesheet, and validation script" in {

    val html =
      """
        |<html>
        |<head></head>
        |<body>Ovo Energy</body>
        |</html>
      """.stripMargin

    val expected =
      """
        |<html>
        |<head>
        |<!-- Statement injected by the Comms Template Manager -->
        |<link href="https://s3-eu-west-1.amazonaws.com/ovo-comms-template-assets/shared/template-validation.js">
        |
        |
        |<!-- Stylesheet injected by the Comms Template Manager -->
        |<link rel="stylesheet" type="text/css" href="https://s3-eu-west-1.amazonaws.com/ovo-comms-template-assets/shared/print-default-styling.css"/>
        |</head>
        |<body>Ovo Energy</body>
        |</html>
      """.stripMargin

    val processedFiles = ProcessedFiles(List(UploadedTemplateFile("templatesPath", html.getBytes, Print, HtmlBody)),
                                        List(UploadedTemplateFile("assetsPath", html.getBytes, Print, HtmlBody)))

    val result       = Injector.injectIntoTemplate(awsContext, processedFiles)
    val resultString = new String(result.right.get.templateFiles(0).contents)

    resultString shouldBe expected
  }

  it should "reject template without <head> tag" in {
    val html =
      """
        |<html>
        |This is my template
        |</html>
      """.stripMargin

    val processedFiles = ProcessedFiles(List(UploadedTemplateFile("templatesPath", html.getBytes, Print, HtmlBody)),
                                        List(UploadedTemplateFile("assetsPath", html.getBytes, Print, HtmlBody)))

    val result = Injector.injectIntoTemplate(awsContext, processedFiles)

    result shouldBe Left(NonEmptyList.of("The template should have a <head> tag."))
  }
}
