package templates

import java.io
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import cats.{Apply, Traverse}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
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
  val headMatcher       = "<head[^>]*>".r

  val sharedFolder = "/shared/"
  val newLine      = Properties.lineSeparator
  val htmlComment  = s"$newLine<!-- Statement injected by the Comms Template Manager -->$newLine"
  val cssComment   = s"$newLine<!-- Stylesheet injected by the Comms Template Manager -->$newLine"

  def getByteTemplate(content: String): Validated[NonEmptyList[String], Array[Byte]] = {
    Valid(content.getBytes())
  }

  def injectIntoTemplate(awsConfig: aws.Context, processedFiles: ProcessedFiles) = {

    def getS3SharedAssetLink(assetName: String) = {
      val region = awsConfig.region.getName
      val bucket = awsConfig.s3TemplateAssetsBucket
      s""""https://s3-$region.amazonaws.com/$bucket$sharedFolder$assetName""""
    }

    def printInjections(html: String): Validated[NonEmptyList[String], String] = {
      injectAssetLink(elementValidation, html) andThen
        injectStyleLink andThen
        injectCharSet
    }

    def getUpdatedTemplate(template: UploadedTemplateFile)(
        contents: Array[Byte]): Validated[NonEmptyList[String], UploadedTemplateFile] =
      Valid(template.mapContent(_ => contents))

    def inject(injectTemplateContent: String => Validated[NonEmptyList[String], String])(
        template: UploadedTemplateFile) = {

      injectTemplateContent(template.utf8Content) andThen
        getByteTemplate andThen
        getUpdatedTemplate(template)
    }

    def injectAssetLink(assetName: String, html: String) = {
      val link = s"<link href=${getS3SharedAssetLink(assetName)}>"
      injectIntoHead(html, s"$htmlComment$link$newLine")
    }

    def injectStyleLink = { (html: String) =>
      injectIntoHead(html, s"""$newLine$cssComment<link rel="stylesheet" type="text/css" href=${getS3SharedAssetLink(
        printCssFileName)}/>$newLine""")
    }

    def injectCharSet = { (html: String) =>
      injectIntoHead(html, s"""$htmlComment<meta charset="utf-8"/>""")
    }

    def injectIntoHead(html: String, element: String) = {
      headMatcher.findFirstIn(html) match {
        case Some(headTag) => Valid(html.replace(headTag, s"$headTag$element"))
        case None          => Invalid(NonEmptyList.of("The template should have a <head> tag."))
      }
    }

    val updatedTemplates = {
      processedFiles.templateFiles.map((template: UploadedTemplateFile) =>
        template.channel match {
          case Print => inject(printInjections)(template)
          case _     => Valid(template)
      })
    }.toList.sequence

    updatedTemplates match {
      case Valid(templates) => Right(ProcessedFiles(templates, processedFiles.assetFiles))
      case Invalid(errors)  => Left(errors)
    }
  }
}
