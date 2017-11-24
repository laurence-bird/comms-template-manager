package templates.validation

import java.nio.file.{Files, Paths}

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.ovoenergy.comms.model.Print
import org.scalatest.{FlatSpec, Matchers}
import templates._

class PrintTemplateValidationSpec extends FlatSpec with Matchers {

  it should "Error if fields are missing from the address box" in {
    val exampleHtml =
      """<html>
        |  <body>
        |     <div id="letterAddress">
        |       {{address.line1}}
        |       {{address.line2}}
        |       {{address.postcode}}
        |       {{address.country}}
        |     </div>
        |  </body>
        |</html>
      """.stripMargin

    val printTemplateFile = List(UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None))
    val result            = PrintTemplateValidation.validatePrintFiles(printTemplateFile)

    result shouldBe Invalid(NonEmptyList.of("Missing expected address placeholder address.town"))
  }

  it should "Error if the address box is missing" in {
    val exampleHtml =
      """<html>
        |     No address, no letter!
        |</html>
      """.stripMargin

    val printTemplateFile = List(UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None))
    val result            = PrintTemplateValidation.validatePrintFiles(printTemplateFile)

    result shouldBe Invalid(NonEmptyList.of("Could not find expected address element with id letterAddress"))
  }

  it should "Error if there are javascript sources included" in {
    val exampleHtml =
      """<html>
        |     <div id="letterAddress">
        |       {{address.line1}}
        |       {{address.line2}}
        |       {{address.town}}
        |       {{address.county}}
        |       {{address.postcode}}
        |       {{address.country}}
        |     </div>
        |     <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>
        |</html>
      """.stripMargin

    val printTemplateFile = List(UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None))
    val result            = PrintTemplateValidation.validatePrintFiles(printTemplateFile)

    result shouldBe Invalid(NonEmptyList.of("Script included in body.html is not allowed"))
  }

  it should "validate against RGB hmtl values" in {
    val exampleHtml =
      """<html>
        |     <head>
        |       <style>
        |         body {
        |           background-color: linen;
        |         }
        |
        |       h1 {
        |         color: maroon;
        |         margin-left: 40px;
        |       }
        |       </style>
        |
        |     </head>
        |     <div id="letterAddress">
        |       {{address.line1}}
        |       {{address.line2}}
        |       {{address.town}}
        |       {{address.county}}
        |       {{address.postcode}}
        |       {{address.country}}
        |     </div>
        |</html>
      """.stripMargin
    val printTemplateFile = List(UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None))
    val result            = PrintTemplateValidation.validatePrintFiles(printTemplateFile)

    result shouldBe Invalid(
      NonEmptyList.of("Non-CMYK colour 'color: linen' referenced in: body.html",
                      "Non-CMYK colour 'color: maroon' referenced in: body.html"))
  }

  it should "Allow inline CMYK encoded css properties" in {
    val exampleHtml =
      """<html>
        |     <head>
        |       <style>
        |       h1 {
        |         color: cmyk(0, 0, 0, 0);
        |         margin-left: 40px;
        |       }
        |       </style>
        |
        |     </head>
        |     <div id="letterAddress">
        |       {{address.line1}}
        |       {{address.line2}}
        |       {{address.town}}
        |       {{address.county}}
        |       {{address.postcode}}
        |       {{address.country}}
        |     </div>
        |</html>
      """.stripMargin
    val printTemplateFile = List(UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None))
    val result            = PrintTemplateValidation.validatePrintFiles(printTemplateFile)

    result shouldBe 'valid
  }

  it should "Error if an invalid image format is present" in {

    val imagePath       = getClass.getResource("/images/donnie.png")
    val file            = Content(Paths.get("test/resources/images/donnie.png"))
    val printImageAsset = List(UploadedTemplateFile("assets/donnie.png", file, Print, Asset, None))

    val result = PrintTemplateValidation.validatePrintFiles(printImageAsset)

    result shouldBe Invalid(NonEmptyList.of("Invalid file type found among assets: assets/donnie.png"))
  }

  it should "Error if an RGB image is included" in {
    val imagePath       = getClass.getResource("/images/donnie.jpg")
    val file            = Content(Paths.get("test/resources/images/donnie.jpg"))
    val printImageAsset = List(UploadedTemplateFile("assets/donnie.jpg", file, Print, Asset, None))

    val result = PrintTemplateValidation.validatePrintFiles(printImageAsset)

    result shouldBe Invalid(
      NonEmptyList.of(
        "Asset assets/donnie.jpg has an invalid colour space, only CMYK colours are supported for print"))
  }

  it should "Allow CMYK image is included" in {
    def templateFileForName(name: String) = {
      List(
        UploadedTemplateFile("assets/cmyk.jpg",
                             Content(Paths.get(s"test/resources/images/$name")),
                             Print,
                             Asset,
                             None))
    }

    val validImageFiles = List("cmyk.jpg", "ovoGreen.tif")

    val results = validImageFiles.map { fileName =>
      val printFile = templateFileForName(fileName)
      PrintTemplateValidation.validatePrintFiles(printFile)
    }

    results.foreach { r =>
      r shouldBe 'valid
    }
  }

  it should "allow lots of valid templateFiles" in {
    val exampleImage = Paths.get(s"test/resources/images/cmyk.jpg")
    val exampleHtml =
      """<html>
        |     <head>
        |       <style>
        |       h1 {
        |         color: cmyk(0, 0, 0, 0);
        |         margin-left: 40px;
        |       }
        |       </style>
        |
        |     </head>
        |     <div id="letterAddress">
        |       {{address.line1}}
        |       {{address.line2}}
        |       {{address.town}}
        |       {{address.county}}
        |       {{address.postcode}}
        |       {{address.country}}
        |     </div>
        |</html>
      """.stripMargin
    val templateFiles = List(
      UploadedTemplateFile("assets/cmyk.jpg", Content(exampleImage), Print, Asset, None),
      UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None)
    )
    PrintTemplateValidation.validatePrintFiles(templateFiles) shouldBe 'valid
  }

  it should "validate CSS assets for non-cmyk colour values" in {
    val exampleCSS =
      """
         |body {
         |  background-color: cmyk(1, 1, 1, 1);
         |}
         |
         |h1 {
         |  color: cmyk(0, 1, 1, 0);
         |}
         |
         |h2 {
         |  bgcolour: cmyk(0, 0, 0, 1);
         |}
      """.stripMargin

    val templateFiles = List(
      UploadedTemplateFile("assets/myStyle.css", Content(exampleCSS), Print, Asset, None)
    )

    PrintTemplateValidation.validatePrintFiles(templateFiles) shouldBe 'valid
  }

  it should "reject if CSS assets contains non-cmyk colour values" in {
    val exampleCSS =
      """
         |body {
         |  background-color: rgb(255, 255, 255);
         |}
         |
         |h1 {
         |  color: green;
         |}
         |
         |h2 {
         |  bgcolour: cmyk(0, 0, 0, 1);
         |}
      """.stripMargin

    val templateFiles = List(
      UploadedTemplateFile("assets/myStyle.css", Content(exampleCSS), Print, Asset, None)
    )

    PrintTemplateValidation.validatePrintFiles(templateFiles) shouldBe Invalid(
      NonEmptyList.of("Non-CMYK colour 'color: rgb' referenced in: assets/myStyle.css",
                      "Non-CMYK colour 'color: green' referenced in: assets/myStyle.css"))
  }

  it should "Error if css file is referenced" in {
    val exampleHtml =
      """<html>
        |     <head>
        |       <link href="https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/letter/letter.css" rel="stylesheet">
        |       <link href="https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/letter/pretty.css" rel="stylesheet">
        |       <style>
        |       h1 {
        |         color: cmyk(0, 0, 0, 0);
        |         margin-left: 40px;
        |       }
        |       </style>
        |
        |     </head>
        |     <div id="letterAddress">
        |       {{address.line1}}
        |       {{address.line2}}
        |       {{address.town}}
        |       {{address.county}}
        |       {{address.postcode}}
        |       {{address.country}}
        |     </div>
        |</html>
      """.stripMargin

    val templateFiles = List(
      UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None)
    )

    PrintTemplateValidation.validatePrintFiles(templateFiles) shouldBe Invalid(NonEmptyList.of(
      "The template should not reference external files: https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/letter/letter.css, https://s3-eu-west-1.amazonaws.com/dev-ovo-comms-template-assets/service/letter/pretty.css"))
  }

  it should "Allow references to css assets " in {
    val exampleHtml =
      """<html>
        |     <head>
        |       <link href="assets/letter.css" rel="stylesheet">
        |       <link href="assets/pretty.css" rel="stylesheet">
        |       <style>
        |       h1 {
        |         color: cmyk(0, 0, 0, 0);
        |         margin-left: 40px;
        |       }
        |       </style>
        |
        |     </head>
        |     <div id="letterAddress">
        |       {{address.line1}}
        |       {{address.line2}}
        |       {{address.town}}
        |       {{address.county}}
        |       {{address.postcode}}
        |       {{address.country}}
        |     </div>
        |</html>
      """.stripMargin

    val templateFiles = List(
      UploadedTemplateFile("body.html", Content(exampleHtml), Print, HtmlBody, None)
    )

    PrintTemplateValidation.validatePrintFiles(templateFiles) shouldBe 'valid
  }
}
