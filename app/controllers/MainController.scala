package controllers

import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.{ZipEntry, ZipFile}

import akka.NotUsed
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import aws.Interpreter.ErrorsOr
import cats.data.NonEmptyList
import cats.~>
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3Client
import com.gu.googleauth.UserIdentity
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates.cache.CachingStrategy
import com.ovoenergy.comms.templates.model.Brand
import com.ovoenergy.comms.templates.model.template.metadata.TemplateSummary
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
import play.api.http.FileMimeTypes

import scala.collection.JavaConversions._
import io.circe._
import io.circe.syntax._
import play.api.Logger
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

  implicit def commTypeToString(commType: CommType): String = commType.getClass.getName

  val s3Client = new AmazonS3ClientWrapper(amazonS3Client, awsContext.s3TemplateFilesBucket)

  val templateContext = TemplatesContext(
    templatesRetriever = new TemplatesS3Retriever(s3Client),
    parser = new HandlebarsParsing(new PartialsS3Retriever(s3Client)),
    cachingStrategy = CachingStrategy.noCache
  )

  private def getCommName(templateId: String) =
    awsContext.dynamo
      .getTemplateSummary(templateId)
      .map(_.commName)
      .getOrElse("Unknown")

  def getPreviewPrint(templateId: String, commVersion: String): Action[PreviewForm] =
    Authenticated(parse.form(PreviewForm.previewPrintForm)).async { req =>
      implicit val ec: ExecutionContext = controllerComponents.executionContext
      val previewRequest                = req.body

      val templateVersion = awsContext.dynamo.listVersions(templateId).find(_.version == commVersion)

      def stream(bytes: ByteString, chunkSize: Int): Source[ByteString, NotUsed] = {
        Source(bytes).grouped(chunkSize).flatMapConcat(bs => Source.single(ByteString(bs: _*)))
      }

      templateVersion
        .fold(Future.successful(NotFound(s"Template $templateId:$commVersion not found"))) { template =>
          composerClient
            .getRenderedPrintPdf(templateId, commVersion, template.commType, previewRequest.templateData)
            .map {
              case Left(TemplateNotFound(message)) => {
                Logger.error(
                  s"Failed to render print preview for template $templateId v$commVersion as template could not be found: $message")
                NotFound(message)
              }
              case Left(UnknownError(message)) => {
                Logger.error(s"Unknown error rendering print preview for template $templateId v$commVersion: $message")
                ServiceUnavailable(message)
              }
              case Right(bytes) =>
                Ok.chunked(stream(bytes, 1024)).as("application/pdf")
            }
        }
        .recover {
          case NonFatal(e) => {
            Logger.error(s"Error thrown rendering print preview for template $templateId v$commVersion", e)
            ServiceUnavailable(e.getMessage)
          }
        }
    }

  def getRequiredData(templateId: String, version: String) = Authenticated { request =>
    implicit val user: UserIdentity = request.user

    val templateManifest = TemplateManifest(templateId, version)

    val requiredFields: Either[NonEmptyList[String], Json] =
      for {
        template     <- TemplatesRepo.getTemplate(templateContext, templateManifest).toEither
        requiredData <- template.requiredData.toEither
        templateData <- TemplateDataGenerator
          .generateTemplateData(requiredData)
          .toRight(NonEmptyList.of("No mandatory fields"))
      } yield {
        templateData.asJson
      }

    requiredFields match {
      case Left(errors) =>
        Logger.error(s"Failed to retrieve required data for template: ${errors.toList.mkString(", ")}")
        NotFound(s"Failed to retrieve required data for template: $errors")
      case Right(fields) =>
        Ok(views.html.templateRequiredData(templateId, version, fields.spaces4))

    }
  }

  import cats.instances.either._

  def getTemplateVersion(templateId: String, version: String) = Authenticated {
    TemplateOp.retrieveTemplate(TemplateManifest(templateId, version)).foldMap(interpreter) match {
      case Left(err) => NotFound(s"Failed to retrieve template: $err")
      case Right(res: ZippedRawTemplate) =>
        val dataContent: Source[ByteString, _] =
          StreamConverters.fromInputStream(() => new ByteArrayInputStream(res.templateFiles))
        Ok.chunked(dataContent)
          .withHeaders(("Content-Disposition", s"attachment; filename=$templateId-$version.zip"))
          .as("application/zip")
    }
  }

  def listTemplates = Authenticated { request =>
    implicit val user = request.user
    TemplateOp.listTemplateSummaries().foldMap(interpreter) match {
      case Left(err) => {
        Logger.error(s"Failed to list templates with errors: ${err.toList.mkString(", ")}")
        NotFound(s"Failed to retrieve templates: $err")
      }
      case Right(res) => Ok(views.html.templateList(res, commPerformanceUrl, commSearchUrl))
    }
  }

  def listVersions(templateId: String) = Authenticated { request =>
    implicit val user = request.user
    val commName      = getCommName(templateId)
    TemplateOp.retrieveAllTemplateVersions(templateId, commName).foldMap(interpreter) match {
      case Left(errs) => {
        Logger.error(s"Failed to list versions of comm $commName with errors: ${errs.toList.mkString(", ")}")
        NotFound(errs.head)
      }
      case Right(versions) => Ok(views.html.templateVersions(versions, commName, templateId))
    }
  }

  def publishNewTemplateGet = Authenticated { implicit request =>
    implicit val user = request.user
    Ok(views.html.publishNewTemplate("inprogress", List[String](), None, None, Brand.allBrands))
  }

  def publishExistingTemplateGet(templateId: String) = Authenticated { implicit request =>
    implicit val user = request.user
    Ok(views.html.publishExistingTemplate("inprogress", List[String](), templateId, getCommName(templateId)))
  }

  def getDataPart[A](part: String, f: String => Option[A])(
      implicit multipartFormRequest: AuthRequest[MultipartFormData[TemporaryFile]]) =
    multipartFormRequest.body.dataParts
      .get(part)
      .flatMap(_.headOption)
      .flatMap(f)

  def publishNewTemplatePost = Authenticated(parse.multipartFormData) {
    implicit multipartFormRequest: AuthRequest[MultipartFormData[TemporaryFile]] =>
      implicit val user: UserIdentity = multipartFormRequest.user

      val result = for {
        commName     <- getDataPart("commName", Some(_))
        commType     <- getDataPart("commType", CommType.fromString)
        brand        <- getDataPart("brand", Brand.fromStringCaseInsensitive)
        templateFile <- multipartFormRequest.body.file("templateFile")
      } yield {

        val templateManifest = TemplateManifest(UUID.randomUUID().toString, "1.0")

        Logger.info(s"Publishing new comm template, ${commName}")
        val uploadedFiles = extractUploadedFiles(templateFile)

        TemplateOp
          .validateAndUploadNewTemplate(templateManifest,
                                        commName,
                                        commType,
                                        brand,
                                        uploadedFiles,
                                        user.username,
                                        templateContext)
          .foldMap(interpreter) match {
          case Right(_) =>
            Redirect(routes.MainController.listVersions(templateManifest.id))
          case Left(errors) =>
            Logger.error(
              s"Failed to publish comm ${commName}, version ${templateManifest.version} with errors: ${errors.toList
                .mkString(", ")}")
            Ok(views.html.publishNewTemplate("error", errors.toList, Some(commName), Some(commType), Brand.allBrands))
        }

      }
      result.getOrElse {
        Ok(views.html.publishNewTemplate("error", List("Missing required fields"), None, None, Brand.allBrands))
      }
  }

  def publishExistingTemplatePost(templateId: String) = Authenticated(parse.multipartFormData) {
    implicit multipartFormRequest =>
      implicit val user = multipartFormRequest.user

      val commName = getCommName(templateId)

      multipartFormRequest.body
        .file("templateFile")
        .map { templateFile =>
          val uploadedFiles = extractUploadedFiles(templateFile)

          TemplateOp
            .validateAndUploadExistingTemplate(templateId, commName, uploadedFiles, user.username, templateContext)
            .foldMap(interpreter) match {
            case Right(newVersion: TemplateSummary) =>
              Ok(
                views.html
                  .publishExistingTemplate(
                    "ok",
                    List(
                      s"Template published: commName: ${newVersion.commName}, commType: ${newVersion.commType}, version: ${newVersion.latestVersion}, templateId: ${newVersion.templateId.value}"),
                    templateId,
                    commName
                  )
              )
            case Left(errors) =>
              Logger.error(
                s"Failed to publish new version of comm ${commName} with errors: ${errors.toList.mkString(", ")}")
              Ok(views.html.publishExistingTemplate("error", errors.toList, templateId, commName))
          }
        }
        .getOrElse {
          Ok(
            views.html
              .publishExistingTemplate("error", List("Unknown issue accessing zip file"), templateId, commName))
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
