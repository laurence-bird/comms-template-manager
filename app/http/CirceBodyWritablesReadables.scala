package http

import akka.util.ByteString
import io.circe.{Encoder, Json, ParsingFailure}
import io.circe.parser._
import play.api.libs.ws.{BodyReadable, BodyWritable, InMemoryBody}

trait CirceBodyWritablesReadables {

  implicit lazy val bodyWritableOfJson: BodyWritable[Json] = {
    BodyWritable(json => InMemoryBody(ByteString.fromString(json.noSpaces)), "application/json")
  }

  implicit lazy val bodyReadableOfEitherOfJson: BodyReadable[Either[ParsingFailure, Json]] = {
    BodyReadable[Either[ParsingFailure, Json]](res => parse(res.body))
  }

}
