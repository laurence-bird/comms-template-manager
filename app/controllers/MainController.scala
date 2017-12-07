package controllers

import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.zip.{ZipEntry, ZipFile}

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import aws.Interpreter.ErrorsOr
import cats.data.NonEmptyList
import cats.~>
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3Client
import com.gu.googleauth.UserIdentity
import com.ovoenergy.comms.model.{CommManifest, CommType, Service}
import com.ovoenergy.comms.templates.cache.CachingStrategy
import com.ovoenergy.comms.templates.{TemplatesContext, TemplatesRepo}
import com.ovoenergy.comms.templates.parsing.handlebars.HandlebarsParsing
import com.ovoenergy.comms.templates.retriever.{PartialsS3Retriever, TemplatesS3Retriever}
import com.ovoenergy.comms.templates.s3.AmazonS3ClientWrapper
import controllers.Auth.AuthRequest
import http.PreviewForm
import logic.{TemplateOp, TemplateOpA}
import models.ZippedRawTemplate
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import templates.{Content, UploadedFile}
import play.api.http.{FileMimeTypes, HttpChunk, HttpEntity}

import scala.collection.JavaConversions._
import io.circe._
import io.circe.syntax._
import play.api.libs.concurrent.Futures
import preview._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import play.api.libs.concurrent.Futures._
import preview.ComposerClient.ComposerError
import preview.ComposerClient.ComposerError.{TemplateNotFound, UnknownError}

class MainController(Authenticated: ActionBuilder[AuthRequest, AnyContent],
                     override val controllerComponents: ControllerComponents,
                     interpreter: ~>[TemplateOpA, ErrorsOr],
                     commPerformanceUrl: String,
                     commSearchUrl: String,
                     libratoMetricsUrl: String,
                     awsContext: aws.Context,
                     amazonS3Client: AmazonS3Client,
                     composerClient: ComposerClient)
    extends AbstractController(controllerComponents)
    with I18nSupport
    with TemplateDataInstances {

  val log = LoggerFactory.getLogger("MainController")

  val healthcheck = Action {
    Ok("OK")
  }

  val index = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  val s3Client = new AmazonS3ClientWrapper(amazonS3Client, awsContext.s3TemplateFilesBucket)

  val templateContext = TemplatesContext(
    templatesRetriever = new TemplatesS3Retriever(s3Client),
    parser = new HandlebarsParsing(new PartialsS3Retriever(s3Client)),
    cachingStrategy = CachingStrategy.noCache
  )

  def getPreviewPrint(commName: String, commVersion: String): Action[PreviewForm] =
    Authenticated(parse.form(PreviewForm.previewPrintForm)).async { req =>
      implicit val ec: ExecutionContext = controllerComponents.executionContext
      val previewRequest                = req.body

      val templateVersion = awsContext.dynamo.listVersions(commName).find(_.version == commVersion)

      templateVersion
        .fold(Future.successful(NotFound(s"Template $commName:$commVersion not found"))) { template =>
          composerClient
            .getRenderedPrintPdf(commName, commVersion, template.commType, previewRequest.templateData)
            .map {
              case Left(TemplateNotFound(message)) => NotFound(message)
              case Left(UnknownError(message))     => ServiceUnavailable(message)
              case Right(bytes)                    => Ok(bytes).as("application/pdf")
            }
        }
        .recover {
          case NonFatal(e) => ServiceUnavailable(e.getMessage)
        }
    }

  def getRequiredData(commName: String, version: String) = Authenticated { request =>
    implicit val user: UserIdentity = request.user

    val getCommManifest: Either[NonEmptyList[String], CommManifest] = {
      awsContext.dynamo.getTemplateSummary(commName).toRight(NonEmptyList.of("No template found")) match {
        case Right(template) => Right(CommManifest(template.commType, template.commName, template.latestVersion))
        case Left(error)     => Left(error)
      }
    }

    val requiredFields: Either[NonEmptyList[String], Json] =
      for {
        commManifest <- getCommManifest
        template     <- TemplatesRepo.getTemplate(templateContext, commManifest).toEither
        requiredData <- template.requiredData.toEither
        templateData <- TemplateDataGenerator
          .generateTemplateData(requiredData)
          .toRight(NonEmptyList.of("No mandatory fields"))
      } yield {
        templateData.asJson
      }

    requiredFields match {
      case Left(errors) =>
        NotFound(s"Failed to retrieve required data for template: $errors")
      case Right(fields) =>
        Ok(views.html.templateRequiredData(commName, version, fields.spaces4))

    }
  }

  import cats.instances.either._

  def getTemplateVersion(commName: String, version: String) = Authenticated {
    TemplateOp.retrieveTemplate(CommManifest(Service, commName, version)).foldMap(interpreter) match {
      case Left(err) => NotFound(s"Failed to retrieve template: $err")
      case Right(res: ZippedRawTemplate) =>
        val dataContent: Source[ByteString, _] =
          StreamConverters.fromInputStream(() => new ByteArrayInputStream(res.templateFiles))
        Ok.chunked(dataContent)
          .withHeaders(("Content-Disposition", s"attachment; filename=$commName-$version.zip"))
          .as("application/zip")
    }
  }

  def listTemplates = Authenticated { request =>
    implicit val user = request.user
    TemplateOp.listTemplateSummaries().foldMap(interpreter) match {
      case Left(err)  => NotFound(s"Failed to retrieve templates: $err")
      case Right(res) => Ok(views.html.templateList(res, commPerformanceUrl, commSearchUrl))
    }
  }

  def listVersions(commName: String) = Authenticated { request =>
    implicit val user = request.user
    TemplateOp.retrieveAllTemplateVersions(commName).foldMap(interpreter) match {
      case Left(errs)      => NotFound(errs.head)
      case Right(versions) => Ok(views.html.templateVersions(versions, commName))
    }
  }

  def publishNewTemplateGet = Authenticated { implicit request =>
    implicit val user = request.user
    Ok(views.html.publishNewTemplate("inprogress", List[String](), None, None))
  }

  def publishExistingTemplateGet(commName: String) = Authenticated { implicit request =>
    implicit val user = request.user
    Ok(views.html.publishExistingTemplate("inprogress", List[String](), commName))
  }

  def publishNewTemplatePost = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user: UserIdentity = multipartFormRequest.user

    val result = for {
      commName     <- multipartFormRequest.body.dataParts.get("commName")
      commType     <- multipartFormRequest.body.dataParts.get("commType")
      templateFile <- multipartFormRequest.body.file("templateFile")
    } yield {
      val commManifest = CommManifest(CommType.CommTypeFromValue(commType.head), commName.head, "1.0")

      val uploadedFiles = extractUploadedFiles(templateFile)
      TemplateOp
        .validateAndUploadNewTemplate(commManifest, uploadedFiles, user.username)
        .foldMap(interpreter) match {
        case Right(_) =>
          Ok(
            views.html.publishNewTemplate("ok",
                                          List(s"Template published: $commManifest"),
                                          Some(commName.head),
                                          Some(commType.head)))
        case Left(errors) =>
          Ok(views.html.publishNewTemplate("error", errors.toList, Some(commName.head), Some(commType.head)))
      }

    }
    result.getOrElse {
      Ok(views.html.publishNewTemplate("error", List("Missing required fields"), None, None))
    }
  }

  def publishExistingTemplatePost(commName: String) = Authenticated(parse.multipartFormData) {
    implicit multipartFormRequest =>
      implicit val user = multipartFormRequest.user

      multipartFormRequest.body
        .file("templateFile")
        .map { templateFile =>
          val uploadedFiles = extractUploadedFiles(templateFile)

          TemplateOp
            .validateAndUploadExistingTemplate(commName, uploadedFiles, user.username)
            .foldMap(interpreter) match {
            case Right(newVersion) =>
              Ok(views.html.publishExistingTemplate("ok", List(s"Template published: $newVersion"), commName))
            case Left(errors) =>
              Ok(views.html.publishExistingTemplate("error", errors.toList, commName))
          }
        }
        .getOrElse {
          Ok(views.html.publishExistingTemplate("error", List("Unknown issue accessing zip file"), commName))
        }
  }

  def operationalMetrics(commName: Option[String], channel: Option[String]) = Authenticated { implicit request =>
    implicit val user = request.user

    val commNameString = commName.getOrElse("").toLowerCase
    val channelString  = channel.getOrElse("").toLowerCase
    val endOfPeriod    = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).toEpochSecond
    val duration       = endOfPeriod - OffsetDateTime.now().minusMonths(1).truncatedTo(ChronoUnit.DAYS).toEpochSecond
    val url =
      s"$libratoMetricsUrl?duration=$duration&end_time=$endOfPeriod&source=%2A$commNameString.$channelString%2A"
    Ok(views.html.operationalMetrics(commName, channel, url))
  }

  private def extractUploadedFiles(templateFile: FilePart[TemporaryFile])(
      implicit fileMimeTypes: FileMimeTypes): List[UploadedFile] = {
    val zip                                       = new ZipFile(templateFile.ref.file)
    val zipEntries: collection.Iterator[ZipEntry] = zip.entries

    zipEntries
      .filter(!_.isDirectory)
      .foldLeft(List[UploadedFile]())((list, zipEntry) => {
        val inputStream = zip.getInputStream(zipEntry)
        try {
          val path    = zipEntry.getName.replaceFirst("^/", "")
          val content = Content(inputStream, zipEntry.getSize)
          UploadedFile(path, content, fileMimeTypes.forFileName(path)) :: list
        } finally {
          IOUtils.closeQuietly(inputStream)
        }
      })

  }
}
