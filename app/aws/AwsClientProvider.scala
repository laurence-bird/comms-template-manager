package aws

import com.amazonaws.auth.{AWSCredentialsProviderChain, AWSStaticCredentialsProvider, BasicAWSCredentials, ContainerCredentialsProvider}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.s3.AmazonS3Client
import play.api.Logger

object AwsClientProvider {

  def genClients(isRunningInCompose: Boolean, region: Regions): (AmazonDynamoDBClient, AmazonS3Client) = {
      if(isRunningInCompose){
        Logger.info("Running in compose")
        System.setProperty("com.amazonaws.sdk.disableCertChecking", "true")
        val awsCreds  = new AWSStaticCredentialsProvider(new BasicAWSCredentials("key", "secret"))
        val dClient: AmazonDynamoDBClient = new AmazonDynamoDBClient(awsCreds).withRegion(region)
        val s3Client: AmazonS3Client      = new AmazonS3Client(awsCreds).withRegion(region)
        dClient.setEndpoint(sys.env("LOCAL_DYNAMO"))
        (dClient, s3Client)
      }else{
        val awsCreds = new AWSCredentialsProviderChain(
          new ContainerCredentialsProvider(),
          new ProfileCredentialsProvider("comms")
        )
        (new AmazonDynamoDBClient(awsCreds).withRegion(region),
          new AmazonS3Client(awsCreds).withRegion(region))
      }
    }
}
