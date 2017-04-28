import java.io.{ByteArrayInputStream, File, IOException}
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream}

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.amazonaws.services.s3.{AmazonS3Client, S3ClientOptions}
import com.ovoenergy.comms.model.{CommManifest, CommType}
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.{Scanamo, Table}
import com.typesafe.config.{ConfigFactory, ConfigParseOptions, ConfigResolveOptions}
import models.{TemplateSummary, TemplateVersion}
import okhttp3._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, Tag}
import util.LocalDynamoDB
import cats.syntax.either._
import aws.dynamo.DynamoFormats._

import scala.collection.JavaConverters._

class ServiceTestIt extends FlatSpec with Matchers with BeforeAndAfterAll {

  object DockerComposeTag extends Tag("DockerComposeTag")

  val config =
    ConfigFactory.load(ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))

  val s3Endpoint                = "http://localhost:4569"
  val rawTemplatesBucket        = config.getString("aws.s3.buckets.rawTemplates")
  val templatesBucket           = config.getString("aws.s3.buckets.templates")
  val assetsBucket              = config.getString("aws.s3.buckets.assets")
  val dynamoUrl                 = "http://localhost:8000"
  val dynamoClient              = LocalDynamoDB.client(dynamoUrl)
  val templateVersionsTableName = config.getString("aws.dynamo.tables.templateVersionTable")
  val templateSummaryTableName  = config.getString("aws.dynamo.tables.templateSummaryTable")

  val s3: AmazonS3Client = new AmazonS3Client(new BasicAWSCredentials("key", "secret"))
    .withRegion(Regions.fromName("eu-west-1"))

  val templateVersionTable = Table[TemplateVersion](templateVersionsTableName)
  val templateSummaryTable = Table[TemplateSummary](templateSummaryTableName)

  val httpClient = new OkHttpClient()

  override def beforeAll() = {
    initialiseS3Bucket()
    createDynamoTables()
    waitForAppToStart()
  }

  override def afterAll() = {
    dynamoClient.deleteTable(templateVersionsTableName)
    dynamoClient.deleteTable(templateSummaryTableName)
  }

  private def createDynamoTables() = {
    LocalDynamoDB.createTable(dynamoClient)(templateVersionsTableName)('commName -> S, 'publishedAt -> N)
    LocalDynamoDB.createTable(dynamoClient)(templateSummaryTableName)('commName  -> S)
    waitUntilTableMade(50)

    def waitUntilTableMade(noAttemptsLeft: Int): (String, String) = {
      try {
        val versionTableStatus = dynamoClient.describeTable(templateVersionsTableName).getTable.getTableStatus
        val summaryTableStatus = dynamoClient.describeTable(templateSummaryTableName).getTable.getTableStatus
        if (versionTableStatus != "ACTIVE" && summaryTableStatus != "ACTIVE" && noAttemptsLeft > 0) {
          Thread.sleep(20)
          waitUntilTableMade(noAttemptsLeft - 1)
        } else (templateSummaryTableName, templateSummaryTableName)
      } catch {
        case e: AmazonDynamoDBException => {
          Thread.sleep(20)
          waitUntilTableMade(noAttemptsLeft - 1)
        }
      }
    }
  }

  private def waitForAppToStart() = {
    def loop(attempts: Int): Unit = {
      if (attempts <= 0)
        fail("App didn't start up :(")
      else {
        println("Waiting for app to start ...")

        Thread.sleep(1000L)

        try {
          val response = makeRequest(new Request.Builder().url("http://localhost:9000/").build())
          response.code shouldBe 200
        } catch {
          case _: Exception => loop(attempts - 1)
        }
      }
    }

    loop(20)
  }

  private def initialiseS3Bucket() = {
    val s3clientOptions = S3ClientOptions.builder().setPathStyleAccess(true).disableChunkedEncoding().build()

    s3.setS3ClientOptions(s3clientOptions)
    s3.setEndpoint(s3Endpoint)
    s3.createBucket(rawTemplatesBucket)
    s3.createBucket(assetsBucket)
    s3.createBucket(templatesBucket)

    s3.putObject(rawTemplatesBucket,
                 "service/template-manager-service-test/0.1/email/subject.txt",
                 "SUBJECT {{profile.firstName}}")
    s3.putObject(rawTemplatesBucket,
                 "service/template-manager-service-test/0.1/email/body.html",
                 "{{> header}} HTML BODY {{amount}}")
    s3.putObject(rawTemplatesBucket,
                 "service/template-manager-service-test/0.1/email/body.txt",
                 "{{> header}} TEXT BODY {{amount}}")
    s3.putObject("ovo-comms-templates", "service/fragments/email/html/header.html", "HTML HEADER")
    s3.putObject("ovo-comms-templates", "service/fragments/email/html/footer.html", "HTML FOOTER")

    Thread.sleep(2000)
  }

  it should "Download a raw template version, and compress the contents in a ZIP file with an appropriate name" taggedAs DockerComposeTag in {
    val commManifest: CommManifest = CommManifest(CommType.Service, "template-manager-service-test", "0.1")

    val url = s"http://localhost:9000/templates/${commManifest.name.toLowerCase}/${commManifest.version}"

    val request = new Request.Builder()
      .url(url)
      .build()

    val response = makeRequest(request)
    response.code shouldBe 200
    response.body().contentType().toString shouldBe "application/zip"

    val zipFileStream = new ZipInputStream(new ByteArrayInputStream(response.body.bytes()))

    def getFileNames(is: ZipInputStream, fileNames: Seq[String]): Seq[String] = {
      try {
        val entry: ZipEntry = is.getNextEntry
        getFileNames(is, fileNames :+ entry.getName)
      } catch {
        case e: Throwable => fileNames
      }
    }

    val fileNames = getFileNames(zipFileStream, Nil)

    fileNames should contain allOf ("email/body.html", "email/body.txt", "email/subject.txt")
  }

  it should "Publish a new valid template, storing the assets and processed template files in the correct bucket" taggedAs DockerComposeTag in {
    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource("/templates/valid-template.zip").getPath
    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", "TEST-COMM")
      .addFormDataPart("commType", "Service")
      .addFormDataPart("templateFile", "valid-template.zip", RequestBody.create(mediaType, new File(path)))
      .build()

    val request = new Request.Builder()
      .url("http://localhost:9000/publish/template")
      .post(requestBody)
      .build()
    val result = makeRequest(request)

    val assetsInBucket       = s3.listObjectsV2(assetsBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val templatesInBucket    = s3.listObjectsV2(templatesBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val rawTemplatesInBucket = s3.listObjectsV2(rawTemplatesBucket).getObjectSummaries.asScala.map(_.getKey).toList

    assetsInBucket should contain("service/TEST-COMM/1.0/email/assets/canary.png")
    templatesInBucket should contain allOf ("service/TEST-COMM/1.0/email/body.html", "service/TEST-COMM/1.0/email/subject.txt", "service/TEST-COMM/1.0/sms/body.txt")
    rawTemplatesInBucket should contain allOf ("service/TEST-COMM/1.0/email/assets/canary.png", "service/TEST-COMM/1.0/email/body.html", "service/TEST-COMM/1.0/email/subject.txt", "service/TEST-COMM/1.0/sms/body.txt")

    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    val templateVersionResult: TemplateVersion = templateVersions.find(_.commName == "TEST-COMM").get
    templateVersionResult.version shouldBe "1.0"
    templateVersionResult.publishedBy shouldBe "dummy.email"
    templateVersionResult.commType shouldBe CommType.Service

    val templateSummaryResult = templateSummaries.find(_.commName == "TEST-COMM").get
    templateSummaries.length shouldBe 1
    templateSummaryResult.latestVersion shouldBe "1.0"
    templateSummaryResult.commType shouldBe CommType.Service

    result.body.string() should include("<ul><li>Template published: CommManifest(Service,TEST-COMM,1.0)</li></ul>")
  }

  it should "Allow publication of a valid new version of an existing template" taggedAs DockerComposeTag in {
    val commName  = "TEST-COMM-2"
    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource("/templates/valid-template.zip").getPath

    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", commName)
      .addFormDataPart("commType", "Service")
      .addFormDataPart("templateFile", "valid-template.zip", RequestBody.create(mediaType, new File(path)))
      .build()

    val request1 = new Request.Builder()
      .url("http://localhost:9000/publish/template")
      .post(requestBody)
      .build()

    val result1 = makeRequest(request1)

    result1.code shouldBe 200

    val request2 = new Request.Builder()
      .url(s"http://localhost:9000/publish/template/$commName")
      .post(requestBody)
      .build()

    val result = makeRequest(request2)

    val assetsInBucket       = s3.listObjectsV2(assetsBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val templatesInBucket    = s3.listObjectsV2(templatesBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val rawTemplatesInBucket = s3.listObjectsV2(rawTemplatesBucket).getObjectSummaries.asScala.map(_.getKey).toList

    assetsInBucket should contain(s"service/$commName/2.0/email/assets/canary.png")
    templatesInBucket should contain allOf (s"service/$commName/2.0/email/body.html", s"service/$commName/2.0/email/subject.txt")
    rawTemplatesInBucket should contain allOf (s"service/$commName/2.0/email/assets/canary.png", s"service/$commName/2.0/email/body.html", s"service/$commName/2.0/email/subject.txt")

    val templateSummaries = scan(templateSummaryTable).filter(_.commName == commName)
    val templateVersions  = scan(templateVersionTable)

    val templateVersionResult = templateVersions.filter(_.commName == commName)

    templateVersionResult.length shouldBe 2
    templateVersionResult.map(_.version) should contain allOf ("1.0", "2.0")

    templateSummaries.length shouldBe 1
    val templateSummaryResult = templateSummaries.find(_.commName == commName).get
    templateSummaryResult.latestVersion shouldBe "2.0"
    templateSummaryResult.commType shouldBe CommType.Service

    result.body.string() should include(
      "<ul><li>Template published: TemplateSummary(TEST-COMM-2,Service,2.0)</li></ul>")
  }

  it should "reject new publication of invalid templates with missing assets" taggedAs DockerComposeTag in {
    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource("/templates/invalid-template.zip").getPath
    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", "INVALID-TEST-COMM")
      .addFormDataPart("commType", "Service")
      .addFormDataPart("templateFile", "invalid-template.zip", RequestBody.create(mediaType, new File(path)))
      .build()

    val request = new Request.Builder()
      .url("http://localhost:9000/publish/template")
      .post(requestBody)
      .build()

    val result            = makeRequest(request)
    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    templateVersions.find(_.commName == "INVALID-TEST-COMM") shouldBe None
    templateSummaries.find(_.commName == "INVALID-TEST-COMM") shouldBe None

    result.body().string() should include(
      "<ul><li>The file email/body.html contains the reference &#x27;assets/thisdoesntexist.png&#x27; to a non-existent asset file</li></ul>")
  }

  it should "reject publication of a new version of a template for one which doesn't exist" taggedAs DockerComposeTag in {
    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource("/templates/valid-template.zip").getPath
    val commName  = "INVALID-TEST-COMM"
    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", "INVALID-TEST-COMM")
      .addFormDataPart("commType", "Service")
      .addFormDataPart("templateFile", "valid-template.zip", RequestBody.create(mediaType, new File(path)))
      .build()

    val request = new Request.Builder()
      .url(s"http://localhost:9000/publish/template/$commName")
      .post(requestBody)
      .build()

    val result            = makeRequest(request)
    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    templateVersions.find(_.commName == "INVALID-TEST-COMM") shouldBe None
    templateSummaries.find(_.commName == "INVALID-TEST-COMM") shouldBe None

    result.body().string() should include("<ul><li>No template found</li></ul>")
  }

  it should "reject publication of an invalid new version of a template" taggedAs DockerComposeTag in {
    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource("/templates/invalid-template.zip").getPath
    val commName  = "TEST-COMM"
    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", "TEST-COMM")
      .addFormDataPart("commType", "Service")
      .addFormDataPart("templateFile", "invalid-template.zip", RequestBody.create(mediaType, new File(path)))
      .build()

    val request = new Request.Builder()
      .url(s"http://localhost:9000/publish/template/$commName")
      .post(requestBody)
      .build()

    val result = makeRequest(request)

    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    templateVersions.find(_.commName == "INVALID-TEST-COMM") shouldBe None
    templateSummaries.find(_.commName == "INVALID-TEST-COMM") shouldBe None

    result.body().string() should include(
      "<ul><li>The file email/body.html contains the reference &#x27;assets/thisdoesntexist.png&#x27; to a non-existent asset file</li></ul>")
  }

  private def scan[A](table: Table[A]): List[A] = {
    val query = table.scan()
    Scanamo.exec(dynamoClient)(query).flatMap(_.toOption)
  }

  private def makeRequest(request: Request) = httpClient.newCall(request).execute()

}
