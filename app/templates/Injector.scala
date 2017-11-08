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

  // Correspond with files in assets directory in this project

  val printCssFileName  = "print-default-styling.css"
  val elementValidation = "template-validation.js"

  val sharedFolder = "/shared/"
  val newLine      = Properties.lineSeparator
  val htmlComment  = s"$newLine<!-- Statement injected by the Comms Template Manager -->$newLine"
  val cssComment   = s"$newLine<!-- Stylesheet injected by the Comms Template Manager -->$newLine"

  def getByteTemplate(content: String): Validated[NonEmptyList[String], Array[Byte]] = {
    Valid(content.getBytes())
  }

  def getStringTemplate(content: Array[Byte]) = new String(content)

  def injectIntoTemplate(awsConfig: aws.Context, processedFiles: ProcessedFiles) = {

    def getS3SharedAssetLink(assetName: String) = {
      val region = awsConfig.region.getName
      val bucket = awsConfig.s3TemplateAssetsBucket
      s""""https://s3-$region.amazonaws.com/$bucket$sharedFolder$assetName""""
    }

    def printInjections(html: String): Validated[NonEmptyList[String], String] = {
      injectAssetLink(elementValidation, html) andThen
        injectStyleLink
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
      val link = s"<link href=${getS3SharedAssetLink(assetName)}>"
      if (html.contains("</head>")) {
        Valid(html.replace("</head>", s"$htmlComment$link$newLine</head>"))
      } else {
        Invalid(NonEmptyList.of("The template should have a <head> tag."))
      }
    }

    def injectStyleLink = { (html: String) =>
      if (html.contains("</head>")) {
        Valid(
          html.replace("</head>",
                       s"""$newLine$cssComment<link rel="stylesheet" type="text/css" href=${getS3SharedAssetLink(
                         printCssFileName)}/>$newLine</head>"""))
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
