package aws

import com.amazonaws.auth.{
  AWSCredentialsProviderChain,
  AWSStaticCredentialsProvider,
  BasicAWSCredentials,
  ContainerCredentialsProvider
}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsyncClient, AmazonDynamoDBClient}
import com.amazonaws.services.s3.AmazonS3Client
import play.api.Logger

object AwsContextProvider {

  def genContext(isRunningInCompose: Boolean, region: Regions): (AmazonDynamoDBAsyncClient, AmazonS3Client) = {
    if (isRunningInCompose) {
      Logger.info("Running in compose")
      System.setProperty("com.amazonaws.sdk.disableCertChecking", "true")
      val awsCreds                           = new AWSStaticCredentialsProvider(new BasicAWSCredentials("key", "secret"))
      val dClient: AmazonDynamoDBAsyncClient = new AmazonDynamoDBAsyncClient(awsCreds).withRegion(region)
      val s3Client: AmazonS3Client           = new AmazonS3Client(awsCreds).withRegion(region)
      dClient.setEndpoint(sys.env("LOCAL_DYNAMO"))
      (dClient, s3Client)
    } else {
      val awsCreds = new AWSCredentialsProviderChain(
        new ContainerCredentialsProvider(),
        new ProfileCredentialsProvider()
      )
      (new AmazonDynamoDBAsyncClient(awsCreds).withRegion(region), new AmazonS3Client(awsCreds).withRegion(region))
    }
  }
}
