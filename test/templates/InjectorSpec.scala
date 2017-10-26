package templates

import com.amazonaws.regions.Regions
import com.ovoenergy.comms.model.Print
import org.scalatest.{FlatSpec, Matchers}
import templates.AssetProcessing.ProcessedFiles

class InjectorSpec extends FlatSpec with Matchers {

  val assetsBucket = "ovo-comms-template-assets"
  val awsContext = aws.Context(null, null, null, null, null, assetsBucket, Regions.EU_WEST_1)

  it should "create style tag" in {

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
        |<style>
        |<!-- Statement injected by the Comms Template Manager -->
        |@prince-pdf {
        |    prince-pdf-output-intent: url("https://s3-eu-west-1.amazonaws.com/ovo-comms-template-assets/shared/WebCoatedSWOP2006Grade5.icc");
        |}
        |
        |<!-- Statement injected by the Comms Template Manager -->
        |@page {
        |    size: 225mm 320mm portrait;
        |    margin: 11.5mm 7.5mm;
        |    @bottom-center{content: element(footerIdentifier)}}
        |footer{position: running(footerIdentifier);}
        |</style>
        |</head>
        |<body>Ovo Energy</body>
        |</html>
      """.stripMargin

    val processedFiles = ProcessedFiles(
      List(UploadedTemplateFile("templatesPath", html.getBytes, Print, HtmlBody)),
      List(UploadedTemplateFile("assetsPath", html.getBytes, Print, HtmlBody)))

    val result = Injector.injectIntoTemplate(awsContext, processedFiles)
    val resultString = new String(result.right.get.templateFiles(0).contents)

    resultString shouldBe expected
  }

  it should "append to style tag" in {

    val html =
      """
        |<html>
        |<head>
        |<style>
        |@page {margin-bottom: 30mm;}
        |</style>
        |</head>
        |<body>Ovo Energy</body>
        |</html>
      """.stripMargin

    val expected =
      """
        |<html>
        |<head>
        |<style>
        |@page {margin-bottom: 30mm;}
        |
        |<!-- Statement injected by the Comms Template Manager -->
        |@prince-pdf {
        |    prince-pdf-output-intent: url("https://s3-eu-west-1.amazonaws.com/ovo-comms-template-assets/shared/WebCoatedSWOP2006Grade5.icc");
        |}
        |
        |<!-- Statement injected by the Comms Template Manager -->
        |@page {
        |    size: 225mm 320mm portrait;
        |    margin: 11.5mm 7.5mm;
        |    @bottom-center{content: element(footerIdentifier)}}
        |footer{position: running(footerIdentifier);}
        |</style>
        |
        |<!-- Statement injected by the Comms Template Manager -->
        |<link href="https://s3-eu-west-1.amazonaws.com/ovo-comms-template-assets/shared/template-validation.js">
        |</head>
        |<body>Ovo Energy</body>
        |</html>
      """.stripMargin

    val processedFiles = ProcessedFiles(
      List(UploadedTemplateFile("templatesPath", html.getBytes, Print, HtmlBody)),
      List(UploadedTemplateFile("assetsPath", html.getBytes, Print, HtmlBody)))

    val result = Injector.injectIntoTemplate(awsContext, processedFiles)
    val resultString = new String(result.right.get.templateFiles(0).contents)

    resultString shouldBe expected
  }
}
