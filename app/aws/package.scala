import aws.dynamo.Dynamo
import aws.s3.AmazonS3ClientWrapper

package object aws {
  case class Context(s3ClientWrapper: AmazonS3ClientWrapper, dynamo: Dynamo, s3TemplatesBucket: String)
}
