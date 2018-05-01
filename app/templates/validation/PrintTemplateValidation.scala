package templates.validation

import java.io.{ByteArrayInputStream, InputStream}

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.ovoenergy.comms.model.Print
import com.ovoenergy.comms.templates.model.template.files.print.PrintTemplateFiles
import com.sksamuel.scrimage.{Image, ImageMetadata, Tag}
import net.ruippeixotog.scalascraper.model.Element
import play.api.Logger
import templates.{Asset, HtmlBody, TemplateErrors, UploadedTemplateFile}

import scala.util.Try
import scala.util.matching.Regex

object PrintTemplateValidation {

  private val fileExtensionRegex            = """\.[0-9a-z]+$""".r
  private val validPrintAssetFileExtensions = List(".jpeg", ".jpg", ".tiff", ".tif")
  private val expectedAddressFields         = List("address.line1", "address.town", "address.postcode")
  private val addressBoxId                  = "letterAddress"
  private val cssColorSpaceRegex =
    """colou{0,1}r:\s(\w*)|(#[0-9a-f]{3}|#(?:[0-9a-f]{2}){2,4}|(rgb|cmyk)a?\((-?\d+%?[,\s]+){2,3}\s*[\d\.]+%?\))""".r // Looks for colour values

  private val validAssetTypes = ".*.tiff|.*.tif|.*.jpeg|.*.jpg|.*.css".r
  private val imgMatcher      = ".*.tiff|.*.tif|.*.jpeg|.*.jpg".r
  private val cssMatcher      = ".*.css".r
  private val validCssMatcher = "^assets/([^/]+).css$".r

  private def validateAddressBox(templateStr: String): TemplateErrors[Boolean] = {
    val addressBoxElementOpt = HtmlContentParser.getElementWithId(htmlStr = templateStr, id = addressBoxId)

    addressBoxElementOpt
      .map { addr =>
        val initialState: TemplateErrors[Boolean] = Valid(true)

        // Fold through expected address fields, accumulating errors if field(s) are missing
        expectedAddressFields.foldLeft(initialState) {
          (accumulator: TemplateErrors[Boolean], expectedAddressPlaceholder) =>
            val result: TemplateErrors[Boolean] = {
              if (addr.text.contains(s"{{$expectedAddressPlaceholder}}"))
                Valid(true)
              else Invalid(NonEmptyList.of(s"Missing expected address placeholder $expectedAddressPlaceholder"))
            }

            (accumulator, result).mapN { case (_, _) => true }
        }
      }
      .getOrElse(Invalid(NonEmptyList.of(s"Could not find expected address element with id $addressBoxId")))
  }

  /*
  Ensure no links to external stylesheets are present, only those included in assets are allowed
  (so we can validated them)
   */

  private def validateExternalStylesheets(printTemplateStr: String): TemplateErrors[Boolean] = {
    val linkElems = HtmlContentParser.getElements(printTemplateStr, "link").getOrElse(Nil)
    val cssLinks = {
      linkElems.flatMap { e =>
        Try(e.attr("href")).toOption
          .flatMap { link =>
            cssMatcher.findFirstIn(link).flatMap { l =>
              if (l.startsWith("assets/"))
                None
              else
                Some(link)
            }
          }
      }
    }

    if (cssLinks.isEmpty)
      Valid(true)
    else
      Invalid(NonEmptyList.of(s"The template should not reference external files: ${cssLinks.mkString(", ")}"))
  }

  private def validateNoJsIncluded(templateStr: String,
                                   printTemplateFile: UploadedTemplateFile): TemplateErrors[Boolean] = {
    val includedScripts = HtmlContentParser.getElement(htmlStr = templateStr, id = "script")

    if (includedScripts.isDefined)
      Invalid(NonEmptyList.of(s"Script included in ${printTemplateFile.path} is not allowed"))
    else Valid(true)
  }

  private def validateImageFormat(printTemplateFile: UploadedTemplateFile): TemplateErrors[UploadedTemplateFile] = {

    def isSupportedFileType: TemplateErrors[UploadedTemplateFile] = {
      // Only CMYK, TIFF or JPEG images are allowed
      val assetExtension = fileExtensionRegex.findFirstIn(printTemplateFile.path)

      if (assetExtension.isEmpty)
        Invalid(NonEmptyList.of(s"Unable to determine file extension for ${printTemplateFile.path}"))
      else if (assetExtension.isDefined && validPrintAssetFileExtensions.contains(assetExtension.get))
        Valid(printTemplateFile)
      else
        Invalid(NonEmptyList.of(s"File extension of $assetExtension is not allowed"))
    }

    def isSupportedColourSpace: TemplateErrors[UploadedTemplateFile] = {
      val colourSpaceTagIdentifiers = Seq("colour space", "photometric interpretation", "color space")

      val colourSpaceTags: Seq[Tag] = {
        printTemplateFile.contents
          .withContentStream(in => Image.fromStream(in))
          .metadata
          .tags
          .filter(t => colourSpaceTagIdentifiers.contains(t.name.toLowerCase))
      }

      val isCmyk = colourSpaceTags.exists(t => t.value.toLowerCase.contains("cmyk"))

      if (isCmyk)
        Valid(printTemplateFile)
      else
        Invalid(
          NonEmptyList.of(
            s"Asset ${printTemplateFile.path} has an invalid colour space, only CMYK colours are supported for print"))
    }

    (isSupportedFileType, isSupportedColourSpace).mapN {
      case (_, _) => printTemplateFile
    }
  }

  private def validateHtmlColourSpace(printTemplateStr: String,
                                      templateFile: UploadedTemplateFile): TemplateErrors[Unit] = {

    val cssElems = HtmlContentParser.getElements(printTemplateStr, "style").getOrElse(Nil)

    cssElems
      .map((elem: Element) => verifyColourUsage(elem.innerHtml, templateFile.path))
      .foldMap(identity)
  }

  private def verifyColourUsage(str: String, fileName: String): TemplateErrors[Unit] = {
    val colourReferences = cssColorSpaceRegex
      .findAllIn(str)
      .matchData
      .toList

    val totalGroups = colourReferences.map { referenceMatch =>
      val colourdesc = referenceMatch.matched
      if (colourdesc.contains("cmyk")) {
        Valid(())
      } else {
        Invalid(NonEmptyList.of(s"Non-CMYK colour '$colourdesc' referenced in: $fileName"))
      }
    }

    totalGroups.foldMap(identity)
  }

  def validatePrintFiles(templateFiles: List[UploadedTemplateFile]): TemplateErrors[List[UploadedTemplateFile]] = {

    def validatePrintFileFile(
        printTemplateFile: UploadedTemplateFile): Validated[NonEmptyList[String], UploadedTemplateFile] = {
      printTemplateFile match {

        case htmlFile if htmlFile.fileType == HtmlBody => {
          val htmlStr = htmlFile.utf8Content
          (validateAddressBox(htmlStr),
           validateHtmlColourSpace(htmlStr, printTemplateFile),
           validateNoJsIncluded(htmlStr, printTemplateFile),
           validateExternalStylesheets(htmlStr))
            .mapN {
              case (_, _, _, _) => printTemplateFile
            }
        }

        case asset if asset.fileType == Asset && validAssetTypes.findFirstMatchIn(asset.path).isEmpty => {
          Invalid(NonEmptyList.of(s"Invalid file type found among assets: ${asset.path}"))
        }

        case imageFile if imageFile.fileType == Asset && imgMatcher.findFirstMatchIn(imageFile.path).isDefined => {
          validateImageFormat(imageFile)
        }

        case cssFile if cssFile.fileType == Asset && cssMatcher.findFirstMatchIn(cssFile.path).isDefined => {
          verifyColourUsage(cssFile.utf8Content, cssFile.path) match {
            case Valid(())    => Valid(cssFile)
            case Invalid(err) => Invalid(err)
          }
        }

        case other => Valid(other)
      }
    }

    val res = templateFiles.collect {
      case printChannelFile if printChannelFile.channel == Print =>
        validatePrintFileFile(printChannelFile)
      case otherFile => Valid(otherFile)
    }
    res.sequence
  }
}
