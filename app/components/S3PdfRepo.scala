package components

import java.io.ByteArrayInputStream
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.ovoenergy.comms.model.print.OrchestratedPrint
import pdf.RenderedPrintPdf

import scala.util.{Failure, Success, Try}

object S3PdfRepo {

  case class S3Config(s3Client: AmazonS3Client, bucketName: String)

  def saveRenderedPdf(renderedPrintPdf: RenderedPrintPdf,
                      incomingEvent: OrchestratedPrint,
                      s3Config: S3Config): Either[String, String] = {

    val s3Client = s3Config.s3Client
    val bucketName = s3Config.bucketName
    val metadata = new ObjectMetadata()
    val inputStream = new ByteArrayInputStream(renderedPrintPdf.pdfBody)
    val key = buildKey(incomingEvent)

    val result = Try(s3Client.putObject(bucketName, key, inputStream, metadata))

    result match {
      case Success(_) => Right(key)
      case Failure(error) => Left(error.getMessage)
    }

  }

  def buildKey(incomingEvent: OrchestratedPrint): String = {
    val commName = incomingEvent.metadata.commManifest.name
    val createdAt = incomingEvent.metadata.createdAt
    val tt = incomingEvent.metadata.traceToken
    val itt = incomingEvent.internalMetadata.internalTraceToken

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateOfCreation = LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault()).format(dateFormatter)

    s"$commName/$dateOfCreation/${createdAt.toEpochMilli}-$tt-$itt.pdf"
  }

}