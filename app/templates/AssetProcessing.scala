package templates

import aws.Interpreter.ErrorsOr
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.amazonaws.regions.{Region, Regions}
import com.ovoenergy.comms.model.{Channel, CommManifest}

object AssetProcessing {

  private val assetTemplateReferenceRegex = "(?:'|\")(?: *)(assets)(?:/[^(\"')]+)(?: *)(?:'|\")".r

  case class ProcessedFiles(templateFiles: List[UploadedFile], assetFiles: List[UploadedFile])

  def processAssets(region: Regions,
                    assetsS3Bucket: String,
                    commManifest: CommManifest,
                    uploadedFiles: List[UploadedFile]): ErrorsOr[ProcessedFiles] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    val nonAssetFiles = UploadedFile.extractNonAssetFiles(uploadedFiles)
    val assetFiles    = uploadedFiles.filterNot(nonAssetFiles.contains)
    val processedTemplateFiles: Validated[NonEmptyList[String], List[UploadedFile]] = nonAssetFiles
      .filter(_.channel.isDefined)
      .traverseU(uploadedFile => {
        replaceAssetReferences(region, assetsS3Bucket, uploadedFile.channel.get, commManifest, uploadedFile.contents)
          .map(contents => uploadedFile.copy(contents = contents))
      })
    processedTemplateFiles.map(ProcessedFiles(_, assetFiles)).toEither
  }

  private def replaceAssetReferences(region: Regions,
                                     assetsS3Bucket: String,
                                     channel: Channel,
                                     commManifest: CommManifest,
                                     contents: Array[Byte]): ValidatedNel[String, Array[Byte]] = {
    def replaceReferences(s3Endpoint: String, contentsString: String) = {
      val replacementAssetsPath = s"$s3Endpoint/assets"
      assetTemplateReferenceRegex
        .replaceAllIn(contentsString, m => m.group(0).replaceFirst(m.group(1), replacementAssetsPath))
        .getBytes
    }
    determineS3Endpoint(region, assetsS3Bucket, channel, commManifest).map(replaceReferences(_, new String(contents)))
  }

  private def determineS3Endpoint(region: Regions,
                                  assetsS3Bucket: String,
                                  channel: Channel,
                                  commManifest: CommManifest): ValidatedNel[String, String] = {
    if (!Region.getRegion(region).isServiceSupported("s3")) {
      Invalid(NonEmptyList.of("S3 not supported in region selected"))
    } else if (!Region.getRegion(region).hasHttpsEndpoint("s3")) {
      Invalid(NonEmptyList.of("No https support for s3 in region selected"))
    } else {
      val s3ServiceEndpoint = Region.getRegion(region).getServiceEndpoint("s3")
      Valid(
        s"https://$s3ServiceEndpoint/$assetsS3Bucket/${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${channel.toString.toLowerCase}")
    }
  }
}
