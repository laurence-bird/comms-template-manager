//import java.util.Date
//
//import aws.AwsContextProvider
//import aws.s3.AmazonS3ClientWrapper
//import com.amazonaws.auth.profile.ProfileCredentialsProvider
//import com.amazonaws.auth.{AWSCredentialsProviderChain, ContainerCredentialsProvider}
//import com.amazonaws.regions.Regions
//import com.amazonaws.services.s3.model.{AmazonS3Exception, ListObjectsV2Request}
//import com.gu.scanamo.{Scanamo, Table}
//import aws.dynamo.DynamoFormats._
//import com.ovoenergy.comms.model.{CommType, Service}
//import models.{TemplateSummary, TemplateVersion}
//import play.api.Logger
//
//import scala.collection.JavaConverters._
//
//object PopulateDynamo {
//
//  // Synchronises templates in S3 with version summaries in the dynamo table
//
//  val awsCreds = new AWSCredentialsProviderChain(
//    new ContainerCredentialsProvider(),
//    new ProfileCredentialsProvider("comms")
//  )
//
//  val (dynamoClient, s3Client) = AwsContextProvider.genContext(false, Regions.EU_WEST_1)
//
//  val s3ClientWrapper = new AmazonS3ClientWrapper(s3Client)
//  val files           = listFiles("ovo-comms-templates-raw", "service/")
//
//  val fileStructures: Seq[(Date, List[String])] = files.map { file =>
//    val key = file.getKey
//    (file.getLastModified, key.split("""\/""").toList)
//  }
//
//  // Extracting comm name, version and date last modified
//  val commDetails = fileStructures
//    .collect {
//      case (date, f) if f.length >= 3 && f(1) != "fragments" => ((f(1), f(2)), date)
//    }
//    .toMap
//    .toSeq
//
//  val templateVersions: Seq[TemplateVersion] = commDetails.map {
//    case ((name, version), date) => TemplateVersion(name, version, date.toInstant, "MigrationScript", Service)
//  }
//
//  val templateVersionTable = Table[TemplateVersion]("template-manager-PRD-TemplateVersionTable-FBZI031JQM9K")
//  val templateSummaryTable = Table[TemplateSummary]("template-manager-PRD-TemplateSummaryTable-ZCCG24NUMTNO")
//
//  val templateSummaries = templateVersions
//    .groupBy(_.commName)
//    .mapValues { v =>
//      val sortedTemplateVersions = v.sortBy(_.publishedAt.toEpochMilli)
//      val version                = sortedTemplateVersions.last
//      TemplateSummary(version.commName, Service, version.version)
//    }
//    .values
//
//  templateVersions.foreach { v =>
//    println(s"writing version to dynamo: $v")
//    val query = templateVersionTable.put(v)
//
//    Scanamo.exec(dynamoClient)(query)
//  }
//
//  templateSummaries.foreach { s =>
//    println(s"writing summary: $s")
//    val query = templateSummaryTable.put(s)
//    Scanamo.exec(dynamoClient)(query)
//  }
//
//  private def listFiles(bucket: String, prefix: String) = {
//    try {
//      val request = new ListObjectsV2Request().withBucketName(bucket).withPrefix(prefix)
//      s3Client.listObjectsV2(request).getObjectSummaries.asScala
//    } catch {
//      case e: AmazonS3Exception =>
//        Logger.warn(s"Failed to list objects under s3://$bucket/$prefix", e)
//        Nil
//    }
//  }
//}
