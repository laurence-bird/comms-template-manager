package http

import cats.implicits._
import com.ovoenergy.comms.model.{CommType, TemplateData}
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import play.api.data.Forms._
import play.api.data.format.Formats.stringFormat
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import play.api.data.format.Formats._
import preview.TemplateDataInstances

case class PreviewForm(templateData: Map[String, TemplateData])

object PreviewForm extends TemplateDataInstances {

  implicit def templateDataFormat(implicit d: Decoder[Map[String, TemplateData]],
                                  e: Encoder[Map[String, TemplateData]]): Formatter[Map[String, TemplateData]] =
    new Formatter[Map[String, TemplateData]] {

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Map[String, TemplateData]] = {
        for {
          str     <- stringFormat.bind(key, data)
          decoded <- decode[Map[String, TemplateData]](str).leftMap(e => Seq(FormError(key, e.getMessage)))
        } yield decoded
      }

      override def unbind(key: String, value: Map[String, TemplateData]): Map[String, String] =
        stringFormat.unbind(key, value.asJson.spaces2)
    }

  val previewPrintForm: Form[PreviewForm] = Form(
    mapping(
      "templateData" -> of[Map[String, TemplateData]]
    )(PreviewForm.apply)(PreviewForm.unapply))
}
