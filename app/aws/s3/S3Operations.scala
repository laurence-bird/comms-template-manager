package aws.s3

import java.io._
import java.util.zip.{ZipEntry, ZipOutputStream}

import aws.Interpreter.ErrorsOr
import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import com.ovoenergy.comms.model.CommManifest
import logic.TemplateOp._

object S3Operations {

  def downloadTemplateFiles(s3ClientWrapper: AmazonS3ClientWrapper,
                            commManifest: CommManifest,
                            bucketName: String): Either[String, TemplateFiles] = {
    val prefix   = buildPrefix(commManifest)
    val fileKeys = s3ClientWrapper.listFiles(bucketName, prefix).right
    fileKeys.flatMap { keys =>
      val result = keys.toList.traverseU { absKey =>
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

  private def buildPrefix(commManifest: CommManifest) = {
    s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}"
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
