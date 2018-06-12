package aws.dynamo

import java.time.{Instant}

import com.gu.scanamo.DynamoFormat
import com.ovoenergy.comms.templates.TemplateMetadataDynamoFormats

trait DynamoFormats extends TemplateMetadataDynamoFormats {

  implicit val instantDynamoFormat =
    DynamoFormat.iso[Instant, Long](Instant.ofEpochMilli)(_.toEpochMilli)

}
