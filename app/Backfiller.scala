import aws.Interpreter.ErrorsOr
import aws.{AwsContextProvider, Context, Interpreter}
import aws.dynamo.Dynamo
import logic.{TemplateOp, TemplateOpA}
import models.{TemplateSummary, TemplateVersion}
import com.gu.scanamo.{Scanamo, Table}
import com.gu.scanamo.syntax._
import aws.dynamo.DynamoFormats._
import aws.s3.AmazonS3ClientWrapper
import cats.{Id, ~>}
import com.amazonaws.auth.{BasicAWSCredentials, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import cats.implicits._
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates
import com.ovoenergy.comms.templates.{TemplatesContext, TemplatesRepo}
import com.ovoenergy.comms.templates.model.template.processed.CommTemplate
import pagerduty.PagerDutyAlerter
import com.ovoenergy.comms.templates.s3.{AmazonS3ClientWrapper => TemplatesLibS3ClientWrapper}
import com.typesafe.config.{ConfigFactory, ConfigParseOptions, ConfigResolveOptions}


object Backfiller extends App {

  val configuration =
    ConfigFactory.load(ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))

  def mandatoryConfig(key: String): String =
    configuration.getString(key)

  lazy val templateVersionsTableName = configuration.getString("aws.dynamo.tables.templateVersionTable")
  lazy val templateSummaryTableName  = configuration.getString("aws.dynamo.tables.templateSummaryTable")

  val s3: AmazonS3Client = new AmazonS3Client(new BasicAWSCredentials("key", "secret"))
    .withRegion(Regions.fromName("eu-west-1"))

  val templateVersionTable = Table[TemplateVersion](templateVersionsTableName)
  val templateSummaryTable = Table[TemplateSummary](templateSummaryTableName)

  val region                        = Regions.EU_WEST_1
  lazy val isRunningInCompose       = false
  lazy val (dynamoClient, s3Client) = AwsContextProvider.genContext(isRunningInCompose, region)

  lazy val dynamo = new Dynamo(
    dynamoClient,
    templateVersionTable,
    templateSummaryTable
  )

  lazy val awsContext = aws.Context(
    templatesS3ClientWrapper = new TemplatesLibS3ClientWrapper(s3Client),
    s3ClientWrapper = new AmazonS3ClientWrapper(s3),
    dynamo = dynamo,
    s3RawTemplatesBucket = mandatoryConfig("aws.s3.buckets.rawTemplates"),
    s3TemplateFilesBucket = mandatoryConfig("aws.s3.buckets.templates"),
    s3TemplateAssetsBucket = mandatoryConfig("aws.s3.buckets.assets"),
    region = region
  )

  lazy val pagerdutyCtxt = PagerDutyAlerter.Context(
    url = mandatoryConfig("pagerduty.url"),
    serviceKey = mandatoryConfig("pagerduty.apiKey"),
    enableAlerts = configuration.getBoolean("pagerduty.alertsEnabled")
  )
  val interpreter = Interpreter.build(awsContext, pagerdutyCtxt)

  backfill(dynamoClient, templateVersionTable, interpreter)

  def backfill(dynamoClient: AmazonDynamoDBClient, templateVersionTable: Table[TemplateVersion], interpreter: ~>[TemplateOpA, ErrorsOr]) = {

    TemplateOp
      .listTemplateSummaries()
      .foldMap(interpreter)
      .map(_.map(getVersions))

    def getVersions(templateSummary: TemplateSummary) = {
      TemplateOp.retrieveAllTemplateVersions(templateSummary.commName).foldMap(interpreter).map(_.map(updateChannels))
    }

    def updateChannels(templateVersion: TemplateVersion) = {
      val channels = fetchChannelsFor(templateVersion)
      val updateStatmement = templateVersionTable.update('commName -> templateVersion.commName and 'publishedAt -> templateVersion.publishedAt, set('channels -> channels))
      Scanamo.exec(dynamoClient)(updateStatmement)
    }


    def fetchChannelsFor(templateVersion: TemplateVersion): List[Channel] = {
      println(s"Current template: ${templateVersion}")
      val template: templates.ErrorsOr[CommTemplate[Id]] = TemplatesRepo.getTemplate(TemplatesContext.cachingContext(new DefaultAWSCredentialsProviderChain, "ovo-comms-templates"),  CommManifest(templateVersion.commType, templateVersion.commName, templateVersion.version))
      template.map(extractChannelFromTemplate).fold(_ => Nil, identity)
    }

    def extractChannelFromTemplate(commTemplate: CommTemplate[Id]) = {
      List(commTemplate.sms.map(_ => SMS),
        commTemplate.email.map(_ => Email),
        commTemplate.print.map(_ => Print)
      ).flatten
    }

  }

}
