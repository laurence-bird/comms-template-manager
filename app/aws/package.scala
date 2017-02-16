import aws.dynamo.Dynamo
import aws.s3.AmazonS3ClientWrapper
import com.ovoenergy.comms.templates.s3.{AmazonS3ClientWrapper => TemplatesAmazonS3ClientWrapper}

package object aws {
  case class Context(templatesS3ClientWrapper: TemplatesAmazonS3ClientWrapper, s3ClientWrapper: AmazonS3ClientWrapper, dynamo: Dynamo, s3TemplatesBucket: String)
}
