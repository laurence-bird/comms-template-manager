package templates

import com.ovoenergy.comms.model.Print
import templates.AssetProcessing.ProcessedFiles

import scala.util.Properties

object Injector {

  val elementValidation: String = "template-validation.js"
  val iccProfile: String = "WebCoatedSWOP2006Grade5.icc"
  val sharedFolder = "/shared/"
  val newLine = Properties.lineSeparator
  val comment = s"$newLine<!-- Statement injected by the Comms Template Manager -->$newLine"

  val getByteTemplate = (content: String) => content.getBytes
  val getStringTemplate = (content: Array[Byte]) => new String(content)

  def injectIntoTemplate(awsConfig: aws.Context, processedFiles: ProcessedFiles) = {

    def getS3AssetLink(assetName: String) = {
      val region  = awsConfig.region.getName
      val bucket  = awsConfig.s3TemplateAssetsBucket
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


    def printInjections =
      injectAssetLink(elementValidation) andThen
      injectStyle(princeICCProfile) andThen
      injectStyle(bleedArea)

    def getUpdatedTemplate(template: UploadedTemplateFile)(contents: Array[Byte]) =
      template.copy(contents = contents)

    def inject(f: String => String)(template: UploadedTemplateFile) = {
      getStringTemplate andThen
      f andThen
      getByteTemplate andThen
      getUpdatedTemplate(template) apply (template.contents)
    }

    def injectAssetLink(assetName: String) =
      (html: String) => {
        val link = s"<link href=${getS3AssetLink(assetName)}>"
        html.replace("</head>", s"$comment$link$newLine</head>")
      }

    def injectStyle(styleElement: String) =
      (html: String) => {
        if(html.contains("</style>")) {
          html.replaceFirst("</style>", s"$comment$styleElement$newLine</style>")
        } else {
          html.replace("</head>", s"$newLine<style>$comment$styleElement$newLine</style>$newLine</head>")
        }
      }

    val updatedTemplates =
      processedFiles.templateFiles.map(template => template.channel match {
        case Print  => inject(printInjections)(template)
        case _     => template
      })

    Right(ProcessedFiles(updatedTemplates, processedFiles.assetFiles))
  }
}
