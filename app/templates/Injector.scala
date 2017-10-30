package templates

import java.io

import cats.{Apply, Traverse}
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import com.ovoenergy.comms.model.Print
import templates.AssetProcessing.ProcessedFiles
import templates.validation.HtmlContentParser
import cats.implicits._
import cats.kernel.Semigroup

import scala.util.Properties

object Injector {

  val elementValidation: String = "template-validation.js"
  val iccProfile: String        = "WebCoatedSWOP2006Grade5.icc"
  val sharedFolder              = "/shared/"
  val newLine                   = Properties.lineSeparator
  val htmlComment               = s"$newLine<!-- Statement injected by the Comms Template Manager -->$newLine"
  val cssComment                = s"$newLine/* Statement injected by the Comms Template Manager */$newLine"

  def getByteTemplate(content: String): Validated[NonEmptyList[String], Array[Byte]] = {
    Valid(content.getBytes())
  }

  def getStringTemplate(content: Array[Byte]) = new String(content)

  def injectIntoTemplate(awsConfig: aws.Context, processedFiles: ProcessedFiles) = {

    def getS3AssetLink(assetName: String) = {
      val region = awsConfig.region.getName
      val bucket = awsConfig.s3TemplateAssetsBucket
      s""""https://s3-$region.amazonaws.com/$bucket$sharedFolder$assetName""""
    }

    val princeICCProfile =
      s"""|@prince-pdf {
          |    prince-pdf-output-intent: url(${getS3AssetLink(iccProfile)});
          |}""".stripMargin

    val bleedArea =
      s"""|@page {
          |    size: 225mm 320mm portrait;
          |    margin: 11.5mm 7.5mm;
          |    @bottom-center{content: element(footerIdentifier)}}
          |footer{position: running(footerIdentifier);}""".stripMargin

    def printInjections(html: String): Validated[NonEmptyList[String], String] = {
      injectAssetLink(elementValidation, html) andThen
        injectStyle(princeICCProfile) andThen
        injectStyle(bleedArea)
    }

    def getUpdatedTemplate(template: UploadedTemplateFile)(
        contents: Array[Byte]): Validated[NonEmptyList[String], UploadedTemplateFile] =
      Valid(template.copy(contents = contents))

    def inject(injectTemplateContent: String => Validated[NonEmptyList[String], String])(
        template: UploadedTemplateFile) = {
      val templateString = getStringTemplate(template.contents)

      injectTemplateContent(templateString) andThen
        getByteTemplate andThen
        getUpdatedTemplate(template)
    }

    def injectAssetLink(assetName: String, html: String): Validated[NonEmptyList[String], String] = {
      val link = s"<link href=${getS3AssetLink(assetName)}>"
      if (html.contains("</head>")) {
        Valid(html.replace("</head>", s"$htmlComment$link$newLine</head>"))
      } else {
        Invalid(NonEmptyList.of("The template should have a <head> tag."))
      }
    }

    def injectStyle(styleElement: String) = { (html: String) =>
      if (html.contains("</style>")) {
        Valid(html.replaceFirst("</style>", s"$cssComment$styleElement$newLine</style>"))
      } else if (html.contains("</head>")) {
        Valid(html.replace("</head>", s"$newLine<style>$cssComment$styleElement$newLine</style>$newLine</head>"))
      } else {
        Invalid(NonEmptyList.of("The template should have a <head> tag."))
      }
    }

    val updatedTemplates = {
      processedFiles.templateFiles.map((template: UploadedTemplateFile) =>
        template.channel match {
          case Print => inject(printInjections)(template)
          case _     => Valid(template)
      })
    }.traverseU(identity)

    updatedTemplates match {
      case Valid(templates) => Right(ProcessedFiles(templates, processedFiles.assetFiles))
      case Invalid(errors)  => Left(errors)
    }

  }
}
