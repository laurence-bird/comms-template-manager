package aws.s3

import java.io._
import java.util.zip.{ZipEntry, ZipOutputStream}

import aws.Interpreter.ErrorsOr
import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import com.ovoenergy.comms.model.{CommManifest, TemplateManifest}
import com.ovoenergy.comms.templates.s3.S3Prefix
import logic.TemplateOp._
import play.api.Logger

object S3Operations {

  def downloadTemplateFiles(s3ClientWrapper: AmazonS3ClientWrapper,
                            templateManifest: TemplateManifest,
                            bucketName: String): Either[String, TemplateFiles] = {
    val prefix = S3Prefix.fromTemplateManifest(templateManifest)
    println(s"Prefix: $prefix")
    val fileKeys = s3ClientWrapper.listFiles(bucketName, prefix).right
    fileKeys.flatMap { keys =>
      val result = keys.toList.traverse { absKey =>
        val file = s3ClientWrapper.downloadFile(bucketName, absKey).right

        val pair: Either[String, (String, Array[Byte])] = file map { f =>
          val strippedKey = absKey.stripPrefix(prefix).dropWhile(_ == '/')
          (strippedKey, f)
        }
        pair
      }
      result.right.map(_.toMap)
    }
  }

  type ByteArray = Array[Byte]

  def compressFiles(templateFiles: TemplateFiles): ErrorsOr[ByteArray] = {
    val baos = new ByteArrayOutputStream()
    try {
      val zip = new ZipOutputStream(baos)
      try {
        templateFiles.foreach {
          case (fileName, file) =>
            zip.putNextEntry(new ZipEntry(fileName))
            zip.write(file)
            zip.closeEntry()
        }
      } finally {
        zip.close()
      }
      val result = baos.toByteArray
      Right(result)
    } catch {
      case e: Throwable => Left(NonEmptyList.of(s"Failed to compress template files: ${e.getMessage}"))
    }
  }
}
