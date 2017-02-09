package aws.s3

import java.nio.charset.StandardCharsets

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{AmazonS3Exception, ListObjectsV2Request}
import com.amazonaws.util.IOUtils
import scala.collection.JavaConverters._
import play.api.Logger

class AmazonS3ClientWrapper(client: AmazonS3Client) {

  case class S3FileDetails(contents: String, key: String, bucket: String)

  def uploadFile(fileDetails: S3FileDetails): Either[String, String] = {
    try {
      client.putObject(fileDetails.bucket, fileDetails.key, fileDetails.contents)
      Right(client.getResourceUrl(fileDetails.bucket, fileDetails.key))
    } catch {
      case e: AmazonS3Exception => Left(s"Failed to upload to aws.s3 with error: ${e.getMessage} for file: ${fileDetails.key} ")
    }
  }

  def downloadFile(bucket: String, key: String): Either[String, String] = {
    try {
        val obj = client.getObject(bucket, key)
        val stream = obj.getObjectContent
        try {
          Right(new String(IOUtils.toByteArray(stream), StandardCharsets.UTF_8))
        } finally {
          stream.close()
        }
      }
    catch {
      case e: AmazonS3Exception =>
        // either the object does not exist or something went really wrong
        Logger.warn(s"Failed to download aws.s3://$bucket/$key", e)
        Left(s"Failed to download s3://$bucket/$key, with status code ${e.getStatusCode}")

    }
  }

  // Returns keys of all the files in specified s3 bucket with the given prefix
  def listFiles(bucket: String, prefix: String): Either[String, Seq[String]]= {
    try {
      val request = new ListObjectsV2Request().withBucketName(bucket).withPrefix(prefix)
      val result = client.listObjectsV2(request).getObjectSummaries.asScala.map(_.getKey)
      Right(result)
    } catch {
      case e: AmazonS3Exception =>
        Logger.warn(s"Failed to list objects under s3://$bucket/$prefix", e)
        Left(s"Failed to retrieve template files from s3://$bucket/$prefix")
    }
  }
}
