package templates

import aws.Interpreter.ErrorsOr
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.amazonaws.regions.{Region, Regions}
import com.ovoenergy.comms.model.{Channel, CommManifest, TemplateManifest}
import com.ovoenergy.comms.templates.s3.S3Prefix

object AssetProcessing {

  private val assetTemplateReferenceRegex = "(?:'|\")(?: *)(assets)(?:/[^(\"')]+)(?: *)(?:'|\")".r

  case class ProcessedFiles(templateFiles: List[UploadedTemplateFile], assetFiles: List[UploadedTemplateFile])

  def processAssets(region: Regions,
                    assetsS3Bucket: String,
                    templateManifest: TemplateManifest,
                    uploadedFiles: List[UploadedTemplateFile]): ErrorsOr[ProcessedFiles] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    val (assetFiles, nonAssetFiles) = uploadedFiles.partition(_.fileType == Asset)
    val processedTemplateFiles: Validated[NonEmptyList[String], List[UploadedTemplateFile]] = nonAssetFiles
      .traverse(templateFile => {
        replaceAssetReferences(region, assetsS3Bucket, templateFile.channel, templateManifest, templateFile.contents)
          .map(contents => templateFile.copy(contents = contents))
      })
    processedTemplateFiles.map(ProcessedFiles(_, assetFiles)).toEither
  }

  private def replaceAssetReferences(region: Regions,
                                     assetsS3Bucket: String,
                                     channel: Channel,
                                     templateManifest: TemplateManifest,
                                     contents: Content): ValidatedNel[String, Content] = {

    def replaceReferences(s3Endpoint: String, contents: Content): Content = {
      val replacementAssetsPath = s"$s3Endpoint/assets"
      contents.mapUtf8(
        contentsString =>
          assetTemplateReferenceRegex
            .replaceAllIn(contentsString, m => m.group(0).replaceFirst(m.group(1), replacementAssetsPath)))
    }

    determineS3Endpoint(region, assetsS3Bucket, channel, templateManifest).map(replaceReferences(_, contents))
  }

  private def determineS3Endpoint(region: Regions,
                                  assetsS3Bucket: String,
                                  channel: Channel,
                                  templateManifest: TemplateManifest): ValidatedNel[String, String] = {
    if (!Region.getRegion(region).isServiceSupported("s3")) {
      Invalid(NonEmptyList.of("S3 not supported in region selected"))
    } else if (!Region.getRegion(region).hasHttpsEndpoint("s3")) {
      Invalid(NonEmptyList.of("No https support for s3 in region selected"))
    } else {
      val s3ServiceEndpoint = Region.getRegion(region).getServiceEndpoint("s3")
      Valid(
        s"https://$s3ServiceEndpoint/$assetsS3Bucket/${S3Prefix.fromTemplateManifest(templateManifest)}/${channel.toString.toLowerCase}")
    }
  }
}
