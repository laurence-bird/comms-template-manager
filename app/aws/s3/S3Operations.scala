package aws.s3

import java.io._
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipOutputStream}

import aws.Interpreter.ErrorsOr
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import com.ovoenergy.comms.model.CommManifest
import logic.TemplateOp._

object S3Operations {

  def downloadTemplateFiles(s3ClientWrapper: AmazonS3ClientWrapper, commManifest: CommManifest, bucketName: String): Either[String, TemplateFiles] = {

    val prefix = buildPrefix(commManifest)
    val keys = s3ClientWrapper.listFiles(bucketName,prefix).toList
    val result = keys.traverseU{ absKey =>
      val file = s3ClientWrapper.downloadFile(bucketName, absKey).right

      val pair: Either[String, (String, Array[Byte])] = file map{ f =>
        val strippedKey = absKey.stripPrefix(prefix).dropWhile(_ == "/")
        (strippedKey, f.getBytes(StandardCharsets.UTF_8))
      }
      pair
    }
    result.right.map(_.toMap)
  }

  private def buildPrefix(commManifest: CommManifest) = {
    s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}"
  }

  type ByteArray = Array[Byte]

  def compressFiles(templateFiles: TemplateFiles): ErrorsOr[ByteArray]= {
    val baos = new ByteArrayOutputStream()
    val zip = new ZipOutputStream(baos)
    try{
      templateFiles.foreach{
        case (fileName, file) =>
          zip.putNextEntry(new ZipEntry(fileName))
          zip.write(file)
          zip.closeEntry()
      }
      Right(baos.toByteArray)
    } catch{
      case e: Throwable => Left(s"Failed to compress template files files: ${e.getMessage}")
    }
    finally{
      zip.close()
    }
  }
}