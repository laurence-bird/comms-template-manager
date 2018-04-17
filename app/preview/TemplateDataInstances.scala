package preview

import io.circe.{Decoder, Encoder, Json}
import shapeless.{Inl, Inr}
import io.circe.syntax._
import io.circe.generic.auto._
import com.ovoenergy.comms.model.TemplateData
import cats.implicits._

trait TemplateDataInstances {

  implicit def templateDataCirceEncoder: Encoder[TemplateData] = Encoder.instance {
    case TemplateData(Inl(value)) =>
      Json.fromString(value)

    case TemplateData(Inr(Inl(value))) =>
      Json.fromValues(value.map(x => x.asJson))

    case TemplateData(Inr(Inr(Inl(value: Map[String, TemplateData])))) =>
      Json.obj(value.mapValues(_.asJson).toSeq: _*)

  }

  implicit def templateDataCirceDecoder: Decoder[TemplateData] = Decoder.instance { hc =>
    hc.value
      .fold(
        Right(TemplateData.fromString("")),
        b => Right(TemplateData.fromString(b.toString)),
        n => Right(TemplateData.fromString(n.toString)),
        s => Right(TemplateData.fromString(s)),
        xs => {
          xs.map(_.as[TemplateData])
            .sequence
            .map(TemplateData.fromSeq)
        },
        obj => {
          obj.toMap
            .map {
              case (key, value) =>
                value.as[TemplateData].map(key -> _)
            }
            .toVector
            .sequence
            .map(x => TemplateData.fromMap(x.toMap))
        }
      )

  }
}
