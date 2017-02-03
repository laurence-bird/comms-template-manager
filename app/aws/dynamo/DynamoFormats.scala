package aws.dynamo

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import com.gu.scanamo.DynamoFormat

object DynamoFormats {

  implicit val instantDynamoFormat =
    DynamoFormat.iso[Instant, Long](Instant.ofEpochMilli)(_.toEpochMilli)

}
