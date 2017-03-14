package controllers

import java.io.ByteArrayInputStream
import java.util.zip.{ZipEntry, ZipFile}

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import aws.Interpreter.ErrorsOr
import cats.data.Validated.{Invalid, Valid}
import cats.instances.either._
import cats.~>
import com.gu.googleauth.GoogleAuthConfig
import com.ovoenergy.comms.model.TemplateData.TD
import com.ovoenergy.comms.model.{CommManifest, CommType, TemplateData}
import com.ovoenergy.comms.templates.model.RequiredTemplateData
import com.ovoenergy.comms.templates.model.RequiredTemplateData._
import logic.{TemplateOp, TemplateOpA}
import models.ZippedRawTemplate
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.twirl.api.Html
import shapeless.Coproduct
import templates.{TemplateRetriever, UploadedFile}

import scala.collection.JavaConversions._
import scala.collection.immutable.SortedMap

class MainController(val authConfig: GoogleAuthConfig,
                     val wsClient: WSClient,
                     val enableAuth: Boolean,
                     interpreter: ~>[TemplateOpA, ErrorsOr],
                     val messagesApi: MessagesApi) extends AuthActions with Controller with I18nSupport {

  val log = LoggerFactory.getLogger("MainController")

  val healthcheck = Action { Ok("OK") }

  val index = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def getTemplateVersion(commName: String, version: String) = Authenticated{ request =>
    TemplateOp.retrieveTemplate(CommManifest(CommType.Service, commName, version)).foldMap(interpreter) match {
      case Left(err) => NotFound(s"Failed to retrieve template: $err")
      case Right(res: ZippedRawTemplate) =>
        val dataContent: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(res.templateFiles))
        Ok.chunked(dataContent).withHeaders(("Content-Disposition", s"attachment; filename=$commName-$version.zip")).as("application/zip")
    }
  }

  def listTemplates = Authenticated { request =>
    implicit val user = request.user
    TemplateOp.listTemplateSummaries().foldMap(interpreter) match {
      case Left(err)  => NotFound(s"Failed to retrieve templates: $err")
      case Right(res) => Ok(views.html.templateList(res))
    }
  }

  def listVersions(commName: String) = Authenticated { request =>
    implicit val user = request.user
    TemplateOp.retrieveAllTemplateVersions(commName).foldMap(interpreter) match {
      case Left(errs)      => NotFound(errs.head)
      case Right(versions) => Ok(views.html.templateVersions(versions, commName))
    }
  }

  def publishNewTemplateGet = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.publishNewTemplate("inprogress", List[String](), None, None))
  }

  def publishExistingTemplateGet(commName: String) = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.publishExistingTemplate("inprogress", List[String](), commName))
  }

  def publishNewTemplatePost = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user = multipartFormRequest.user

    val result = for {
      commName <- multipartFormRequest.body.dataParts.get("commName")
      commType <- multipartFormRequest.body.dataParts.get("commType")
      templateFile <- multipartFormRequest.body.file("templateFile")
    } yield {
      val commManifest = CommManifest(CommType.CommTypeFromValue(commType.head), commName.head, "1.0")


      val uploadedFiles = extractUploadedFiles(templateFile)
      TemplateOp.validateAndUploadNewTemplate(commManifest, uploadedFiles, user.username).foldMap(interpreter) match {
        case Right(_)     => Ok(views.html.publishNewTemplate("ok", List(s"Template published: $commManifest"), Some(commName.head), Some(commType.head)))
        case Left(errors) => Ok(views.html.publishNewTemplate("error", errors.toList, Some(commName.head), Some(commType.head)))
      }
    }
      result.getOrElse {
      Ok(views.html.publishNewTemplate("error", List("Missing required fields"), None, None))
    }
  }

  def publishExistingTemplatePost(commName: String) = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user = multipartFormRequest.user

    multipartFormRequest.body.file("templateFile").map { templateFile =>
      val uploadedFiles = extractUploadedFiles(templateFile)
      TemplateOp.validateAndUploadExistingTemplate(commName, uploadedFiles, user.username).foldMap(interpreter) match {
        case Right(newVersion)  => Ok(views.html.publishExistingTemplate("ok", List(s"Template published: $newVersion"), commName))
        case Left(errors)       => Ok(views.html.publishExistingTemplate("error", errors.toList, commName))
      }
    }.getOrElse {
      Ok(views.html.publishExistingTemplate("error", List("Unknown issue accessing zip file"), commName))
    }
  }

  def testComplexTemplate = Authenticated { implicit request =>
    implicit val user = request.user
    Ok(views.html.test("complex", "2.0", complexRequiredTemplateData))
  }

  def testSimpleTemplate = Authenticated { implicit request =>
    implicit val user = request.user
    Ok(views.html.test("simple", "2.0", simpleRequiredTemplateData))
  }

  def testTemplate(commName: String, commVersion: String) = Authenticated { implicit request =>
    implicit val user = request.user

    val manifest = CommManifest(CommType.Service, commName, commVersion)
    TemplateRetriever.getTemplateRequiredData(manifest) match {
      case Valid(data)  => Ok(views.html.test(commName, commVersion, data))
      case Invalid(err) => NotFound(s"Failed to retrieve template data: $err")
    }
  }

  def processTemplate(commName: String, commVersion: String) = Authenticated { implicit request =>
    implicit val user = request.user

    def processData(requiredTemplateData: RequiredTemplateData.obj) = {
      val userData = request.body.asFormUrlEncoded.get.map { case (key, values) =>
        (key, values.head)
      }

      def determineKeyRegexForString(previousRegex: Option[String], key: String) = {
        if (previousRegex.isDefined) s"${previousRegex.get}\\.$key" + "$"
        else s"^$key" + "$"
      }

      def determineKeyRegexForStringSeq(previousRegex: Option[String], key: String) = {
        if (previousRegex.isDefined) s"${previousRegex.get}\\.$key\\[[0-9]+\\]" + "$"
        else s"^$key\\[[0-9]+\\]" + "$"
      }

      def determineKeyRegexForObj(previousRegex: Option[String], key: String) = {
        if (previousRegex.isDefined) s"${previousRegex.get}\\.$key"
        else s"^$key"
      }

      def determineKeyRegexForObjSeq(previousRegex: Option[String], key: String) = {
        if (previousRegex.isDefined) s"(${previousRegex.get}\\.$key\\[[0-9]+\\]).*"
        else s"(^$key\\[[0-9]+\\]).*"
      }

     def processFields(userData: Map[String, String], previousRegex: Option[String], fields: RequiredTemplateData.Fields): Map[String, TemplateData] = {
        fields.flatMap{ case(key, data) =>

          data match {
            case RequiredTemplateData.string =>
              val userDataKeyRegex = determineKeyRegexForString(previousRegex, key)
              userData
                .filterKeys(_ matches userDataKeyRegex)
                .values
                .headOption
                .map(value => (key, TemplateData(Coproduct[TemplateData.TD](value))))

            case RequiredTemplateData.optString =>
              val userDataKeyRegex = determineKeyRegexForString(previousRegex, key)
              userData
                .filterKeys(_ matches userDataKeyRegex)
                .values
                .headOption
                .map(value => (key, TemplateData(Coproduct[TemplateData.TD](value))))

            case RequiredTemplateData.strings =>
              val userDataKeyRegex = determineKeyRegexForStringSeq(previousRegex, key)
              val values = userData
                .filterKeys(_ matches userDataKeyRegex)
                .map(value => TemplateData(Coproduct[TemplateData.TD](value._2)))
                .toSeq
              Some((key, TemplateData(Coproduct[TemplateData.TD](values))))

            case RequiredTemplateData.obj(f)    =>
              val userDataKeyRegex = determineKeyRegexForObj(previousRegex, key)
              val objTemplateData = processFields(userData, Some(userDataKeyRegex), f)
              Some((key, TemplateData(Coproduct[TemplateData.TD](objTemplateData))))

            case RequiredTemplateData.optObj(f) =>
              val userDataKeyRegex = determineKeyRegexForObj(previousRegex, key)
              val objTemplateData = processFields(userData, Some(userDataKeyRegex), f)
              Some((key, TemplateData(Coproduct[TemplateData.TD](objTemplateData))))

            case RequiredTemplateData.objs(f)   =>
              val userDataKeyRegexString = determineKeyRegexForObjSeq(previousRegex, key)
              val userDataRegex = userDataKeyRegexString r
              val relevantUserDataKeys = userData
                  .keys
                  .flatMap{ relevantUserDataKey =>
                    userDataRegex.findAllIn(relevantUserDataKey).matchData.map(m => m.group(1))
                  }
                  .toSet

              val objTemplateData: Seq[TemplateData] = relevantUserDataKeys
                .flatMap { relevantKey =>
                  val templateDataSeq: Map[String, TemplateData] = userData
                    .filterKeys(_.startsWith(relevantKey))
                    .map{ case(k, v) =>
                      val thisKey = k.substring(k.lastIndexOf(".") + 1).trim
                      (thisKey, TemplateData(Coproduct[TemplateData.TD](v)))
                    }

                  Some(TemplateData(Coproduct[TemplateData.TD](templateDataSeq)))
                }
                .toList

              Some((key, TemplateData(Coproduct[TemplateData.TD](objTemplateData))))
          }
        }
      }

      val templateData = processFields(userData, None, requiredTemplateData.fields)

      Ok(s"Required = $requiredTemplateData xxxxxxxxxxxxxxxxxx Provided = $userData xxxxxxxxxxxxxx TemplateData = $templateData")
    }

    commName match {
      case "complex" => processData(complexRequiredTemplateData)
      case "simple"  => processData(simpleRequiredTemplateData)
      case _ =>
        val manifest = CommManifest(CommType.Service, commName, commVersion)
        TemplateRetriever.getTemplateRequiredData(manifest) match {
            case Valid(data) => processData(data)
            case Invalid(err) => NotFound(s"Failed to retrieve template data: $err")
          }
        }
  }

  private def extractUploadedFiles(templateFile: FilePart[TemporaryFile]): List[UploadedFile] = {
    val zip = new ZipFile(templateFile.ref.file)
    val zipEntries: collection.Iterator[ZipEntry] = zip.entries

    zipEntries.filter(!_.isDirectory)
      .foldLeft(List[UploadedFile]())((list, zipEntry) => {
        val inputStream = zip.getInputStream(zipEntry)
        try {
          val path = zipEntry.getName.replaceFirst("^/", "")
          UploadedFile(path, IOUtils.toByteArray(inputStream)) :: list
        } finally {
          IOUtils.closeQuietly(inputStream)
        }
      })
  }

  private val complexRequiredTemplateData = {
    RequiredTemplateData.obj(SortedMap(
      "aString"          -> string,
      "anOptionalString" -> optString,
      "aListOfStrings"   -> strings,
      "anObject"         -> obj(SortedMap(
        "aString"          -> string,
        "anOptionalString" -> optString,
        "aListOfStrings"   -> strings,
        "anOptionalObject" -> optObj(SortedMap(
          "aString"          -> string,
          "anOptionalString" -> optString,
          "aListOfStrings"   -> strings
        ))
      )),
      "aListOfObjects"  -> objs(SortedMap(
        "aString"          -> string,
        "anOptionalString" -> optString,
        "aListOfStrings"   -> strings
      ))
    ))
  }

  private val simpleRequiredTemplateData = {
    RequiredTemplateData.obj(SortedMap(
      "aString" -> string,
      "anOptionalString" -> optString,
      "aListOfStrings" -> strings,
      "anObject"         -> obj(SortedMap(
        "aString"          -> string
      )),
      "anOptionalObject"         -> optObj(SortedMap(
        "aString"          -> string
      ))
    ))
  }
}
