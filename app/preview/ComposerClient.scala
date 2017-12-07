package preview

import java.util.Base64

import akka.util.ByteString
import com.ovoenergy.comms.model.{CommType, TemplateData}
import http.CirceBodyWritablesReadables
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.slf4j.LoggerFactory
import play.api.libs.ws.WSClient
import preview.ComposerClient.{ComposerError, PreviewRequest, PreviewResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import io.circe.parser._
import cats.syntax.either._

import preview.ComposerClient.ComposerError.{TemplateNotFound, UnknownError}

class ComposerClient(wsClient: WSClient, composerApiEndpoint: String) extends CirceBodyWritablesReadables {

  private val log = LoggerFactory.getLogger(getClass)

  def getRenderedPrintPdf(commName: String,
                          commVersion: String,
                          commType: CommType,
                          templateData: Map[String, TemplateData])(
      implicit ec: ExecutionContext): Future[Either[ComposerError, ByteString]] = {

    val requestUrl = s"$composerApiEndpoint/render/$commName/$commVersion/$commType/print"

    wsClient
      .url(requestUrl)
      .post(PreviewRequest(templateData).asJson)
      .map {
        case response if response.status == 200 =>
          decode[PreviewResponse](response.body)
            .leftMap(e => UnknownError(e.getMessage))
            .map(_.renderedPrint)

        case response if response.status == 404 =>
          decode[TemplateNotFound](response.body)
            .leftMap(e => UnknownError(e.getMessage))
            .fold(Left.apply, Left.apply)

        case response =>
          decode[UnknownError](response.body)
            .leftMap(e => UnknownError(e.getMessage))
            .fold(Left.apply, Left.apply)

      }
  }

}

object ComposerClient extends TemplateDataInstances {

  sealed trait ComposerError {
    def message: String
  }

  object ComposerError {

    case class TemplateNotFound(message: String) extends ComposerError
    object TemplateNotFound {
      implicit val circeDecoder: Decoder[TemplateNotFound] = deriveDecoder[TemplateNotFound]
    }
    case class UnknownError(message: String) extends ComposerError
    object UnknownError {
      implicit val circeDecoder: Decoder[UnknownError] = deriveDecoder[UnknownError]
    }

  }

  case class PreviewRequest(data: Map[String, TemplateData])

  object PreviewRequest {
    implicit val circeEncoder: Encoder[PreviewRequest] = deriveEncoder[PreviewRequest]
  }

  case class PreviewResponse(renderedPrint: ByteString)

  object PreviewResponse {
    implicit val circeDecoder: Decoder[PreviewResponse] = Decoder.decodeString
      .prepare(_.downField("renderedPrint"))
      .emapTry(base64 => Try(Base64.getDecoder.decode(base64)))
      .map(ByteString.apply)
      .map(PreviewResponse.apply)
  }
}
