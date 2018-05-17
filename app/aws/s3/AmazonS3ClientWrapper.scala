package aws.s3

import java.io.ByteArrayInputStream
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{AmazonS3Exception, ListObjectsV2Request, ObjectMetadata}
import org.apache.commons.compress.utils.IOUtils

import scala.collection.JavaConverters._
import play.api.Logger

case class S3FileDetails(contents: Array[Byte], key: String, bucket: String, contentType: Option[String] = None)

class AmazonS3ClientWrapper(client: AmazonS3Client) {

  def uploadFile(fileDetails: S3FileDetails): Either[String, String] = {
    val stream = new ByteArrayInputStream(fileDetails.contents)
    try {

      val meta = new ObjectMetadata()
      meta.setContentLength(fileDetails.contents.length)

      fileDetails.contentType.foreach { c =>
        meta.setContentType(c)
      }

      client.putObject(fileDetails.bucket, fileDetails.key, stream, meta)
      Logger.info(s"Uploaded file to S3: ${fileDetails.bucket} - ${fileDetails.key}")
      Right(client.getResourceUrl(fileDetails.bucket, fileDetails.key))
    } catch {
      case e: AmazonS3Exception =>
        Left(s"Failed to upload to aws.s3 with error: ${e.getMessage} for file: ${fileDetails.key} ")
    } finally {
      IOUtils.closeQuietly(stream)
    }
  }

  def downloadFile(bucket: String, key: String): Either[String, Array[Byte]] = {
    try {
      val obj    = client.getObject(bucket, key)
      val stream = obj.getObjectContent
      try {
        Right(IOUtils.toByteArray(stream))
      } finally {
        stream.close()
      }
    } catch {
      case e: AmazonS3Exception =>
        // either the object does not exist or something went really wrong
        Logger.warn(s"Failed to download aws.s3://$bucket/$key", e)
        Left(s"Failed to download s3://$bucket/$key, with status code ${e.getStatusCode}")

    }
  }

  // Returns keys of all the files in specified s3 bucket with the given prefix
  def listFiles(bucket: String, prefix: String): Either[String, Seq[String]] = {
    println(s"Listing: $bucket/$prefix")
    client.listObjects(bucket, prefix).getObjectSummaries.forEach(o => println(o.getKey))
    try {
      val request = new ListObjectsV2Request().withBucketName(bucket).withPrefix(prefix)
      val result  = client.listObjectsV2(request).getObjectSummaries.asScala.map(_.getKey)
      Right(result)
    } catch {
      case e: AmazonS3Exception =>
        Logger.warn(s"Failed to list objects under s3://$bucket/$prefix", e)
        Left(s"Failed to retrieve template files from s3://$bucket/$prefix")
    }
  }
}
