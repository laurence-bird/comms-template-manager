import java.io.{ByteArrayInputStream, File}
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipInputStream}

import akka.util.ByteString
import aws.dynamo.DynamoFormats
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.amazonaws.services.s3.{AmazonS3Client, S3ClientOptions}
import com.ovoenergy.comms.model._
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.{Scanamo, Table}
import com.typesafe.config.{ConfigFactory, ConfigParseOptions, ConfigResolveOptions}
import models.{TemplateSummaryOps, TemplateVersion}
import okhttp3._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, Tag}
import util.{LocalDynamoDB, MockServerFixture}
import cats.syntax.either._
import com.google.common.io.Resources
import com.ovoenergy.comms.templates.TemplateMetadataDynamoFormats
import com.ovoenergy.comms.templates.model.template.metadata.TemplateSummary
import com.ovoenergy.comms.templates.s3.S3Prefix
import com.ovoenergy.comms.templates.util.Hash
import org.mockserver.model.{HttpRequest, HttpResponse}

import scala.collection.JavaConverters._

class ServiceTestIt extends FlatSpec with Matchers with MockServerFixture with BeforeAndAfterAll with DynamoFormats {

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

  val httpClient = new OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

  override def beforeAll() = {
    super.beforeAll()
    initialiseS3Bucket()
    createDynamoTables()
    waitForAppToStart()
  }

  override def afterAll() = {
    dynamoClient.deleteTable(templateVersionsTableName)
    dynamoClient.deleteTable(templateSummaryTableName)
    super.afterAll()
  }

  private def createDynamoTables() = {
    LocalDynamoDB.createTable(dynamoClient)(templateVersionsTableName)('templateId -> S, 'version -> S)
    LocalDynamoDB.createTable(dynamoClient)(templateSummaryTableName)('templateId  -> S)
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
          val response = makeRequest(new Request.Builder().url("http://localhost:9000/index").build())
          response.code shouldBe 200
          response.close()
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

    s3.putObject(templatesBucket, "fragments/email/html/header.html", "HTML HEADER")
    s3.putObject(templatesBucket, "fragments/email/html/footer.html", "HTML FOOTER")

    Thread.sleep(2000)
  }

  def createNewTemplate(commName: String, filePath: String = "valid-template.zip") = {

    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource(s"/templates/$filePath").getPath

    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", commName)
      .addFormDataPart("commType", "Service")
      .addFormDataPart("brand", "Ovo")
      .addFormDataPart("templateFile", filePath, RequestBody.create(mediaType, new File(path)))
      .build()

    val request = new Request.Builder()
      .url("http://localhost:9000/publish/template")
      .post(requestBody)
      .build()

    makeRequest(request)
  }

  def createNewVersion(commName: String, templateId: String, fileName: String = "valid-template.zip") = {
    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource(s"/templates/$fileName").getPath
    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", commName)
      .addFormDataPart("commType", "Service")
      .addFormDataPart("templateFile", fileName, RequestBody.create(mediaType, new File(path)))
      .build()

    val request = new Request.Builder()
      .url(s"http://localhost:9000/publish/template/$templateId")
      .post(requestBody)
      .build()

    makeRequest(request)
  }

  def getTemplateId(result: String): String = {
    val token = "/publish/template/"

    if (!result.contains(token))
      fail("TemplateId not found in string")

    // strip $token prefix from the string
    val start = result.indexOf(token) + token.length

    val templateId = result
      .drop(start)
      .takeWhile(_ != '"')

    templateId
  }

  it should "Download a raw template version, and compress the contents in a ZIP file with an appropriate name" taggedAs DockerComposeTag in {

    val result     = createNewTemplate("template-manager-service-test")
    val resultStr  = result.body.string()
    val templateId = getTemplateId(resultStr)

    val prefix = getPrefix(templateId, "1.0")

    s3.putObject(rawTemplatesBucket, s"$prefix/email/subject.txt", "SUBJECT {{profile.firstName}}")
    s3.putObject(rawTemplatesBucket, s"$prefix/email/body.html", "{{> header}} HTML BODY {{amount}}")
    s3.putObject(rawTemplatesBucket, s"$prefix/email/body.txt", "{{> header}} TEXT BODY {{amount}}")

    val url = s"http://localhost:9000/templates/$templateId/1.0"

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

    val result     = createNewTemplate("TEST-COMM")
    val resultStr  = result.body.string()
    val templateId = getTemplateId(resultStr)

    result.code() shouldBe 200
    val assetsInBucket       = s3.listObjectsV2(assetsBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val templatesInBucket    = s3.listObjectsV2(templatesBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val rawTemplatesInBucket = s3.listObjectsV2(rawTemplatesBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val prefix               = getPrefix(templateId, "1.0")

    assetsInBucket should contain(s"$prefix/email/assets/canary.png")
    templatesInBucket should contain allOf (s"$prefix/email/body.html", s"$prefix/email/subject.txt", s"$prefix/sms/body.txt")
    rawTemplatesInBucket should contain allOf (s"$prefix/email/assets/canary.png", s"$prefix/email/body.html", s"$prefix/email/subject.txt", s"$prefix/sms/body.txt")

    val templateSummaries                      = scan(templateSummaryTable)
    val templateVersions                       = scan(templateVersionTable)
    val templateVersionResult: TemplateVersion = templateVersions.find(_.commName == "TEST-COMM").get

    templateVersionResult.version shouldBe "1.0"
    templateVersionResult.publishedBy shouldBe "dummy.email"
    templateVersionResult.commType shouldBe Service

    val templateSummaryResult = templateSummaries.find(_.commName == "TEST-COMM").get
    templateSummaryResult.latestVersion shouldBe "1.0"
    templateSummaryResult.commType shouldBe Service

    resultStr should include(s"<h1>Template versions for 'TEST-COMM'</h1>")
    resultStr should include(s"<p>Template ID: <strong>$templateId</strong></p>")
  }

  it should "Allow publication of a valid new version of an existing template" taggedAs DockerComposeTag in {
    val commName  = "TEST-COMM-2"
    val commType  = "Service"
    val mediaType = MediaType.parse("application/zip")
    val path      = getClass.getResource("/templates/valid-template.zip").getPath

    val requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("commName", commName)
      .addFormDataPart("commType", commType)
      .addFormDataPart("brand", "Ovo")
      .addFormDataPart("templateFile", "valid-template.zip", RequestBody.create(mediaType, new File(path)))
      .build()

    val request1 = new Request.Builder()
      .url("http://localhost:9000/publish/template")
      .post(requestBody)
      .build()

    val result1    = createNewTemplate(commName)
    val templateId = getTemplateId(result1.body().string())

    result1.code shouldBe 200

    val request2 = new Request.Builder()
      .url(s"http://localhost:9000/publish/template/$templateId")
      .post(requestBody)
      .build()

    val result = makeRequest(request2)

    val assetsInBucket       = s3.listObjectsV2(assetsBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val templatesInBucket    = s3.listObjectsV2(templatesBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val rawTemplatesInBucket = s3.listObjectsV2(rawTemplatesBucket).getObjectSummaries.asScala.map(_.getKey).toList

    val prefix = getPrefix(templateId, "2.0")

    assetsInBucket should contain(s"$prefix/email/assets/canary.png")
    templatesInBucket should contain allOf (s"$prefix/email/body.html", s"$prefix/email/subject.txt")
    rawTemplatesInBucket should contain allOf (s"$prefix/email/assets/canary.png", s"$prefix/email/body.html", s"$prefix/email/subject.txt")

    val templateSummaries = scan(templateSummaryTable).filter(_.commName == commName)
    val templateVersions  = scan(templateVersionTable)

    val templateVersionResult = templateVersions.filter(_.commName == commName)

    templateVersionResult.length shouldBe 2
    templateVersionResult.map(_.version) should contain allOf ("1.0", "2.0")

    templateSummaries.length shouldBe 1
    val templateSummaryResult = templateSummaries.find(_.commName == commName).get
    templateSummaryResult.latestVersion shouldBe "2.0"
    templateSummaryResult.commType shouldBe Service

    result.body.string() should include(
      s"<li>Template published: commName: $commName, commType: $commType, version: 2.0, templateId: $templateId")

  }

  it should "reject new publication of invalid templates with missing assets" taggedAs DockerComposeTag in {

    val result            = createNewTemplate("INVALID-TEST-COMM", "invalid-template.zip")
    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    templateVersions.find(_.commName == "INVALID-TEST-COMM") shouldBe None
    templateSummaries.find(_.commName == "INVALID-TEST-COMM") shouldBe None

    result.body().string() should include(
      "<ul><li>The file email/body.html contains the reference &#x27;assets/thisdoesntexist.png&#x27; to a non-existent asset file</li></ul>")
  }

  it should "reject publication of a new version of a template for one which doesn't exist" taggedAs DockerComposeTag in {
    val commName          = "NON-EXISTING-COMM"
    val result            = createNewVersion(commName, "1234")
    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    templateVersions.find(_.commName == commName) shouldBe None
    templateSummaries.find(_.commName == commName) shouldBe None

    result.body().string() should include("<ul><li>TemplateSummary for templateId 1234 has not been found</li></ul>")
  }

  it should "reject publication of an invalid new version of a template" taggedAs DockerComposeTag in {
    val commName        = "TEST-COMM-3"
    val invalidFileName = "invalid-template.zip"

    val response = createNewTemplate(commName)
    val result   = createNewVersion(commName, getTemplateId(response.body.string), invalidFileName)

    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    templateVersions.find(_.commName == commName).size shouldBe 1
    templateSummaries.find(_.commName == commName).size shouldBe 1

    result.body().string() should include(
      "<ul><li>The file email/body.html contains the reference &#x27;assets/thisdoesntexist.png&#x27; to a non-existent asset file</li></ul>")
  }

  it should "Publish a new valid template with print, storing the assets and processed template files in the correct bucket" taggedAs DockerComposeTag in {

    val result               = createNewTemplate("TEST-COMM-PRINT", "valid-with-print.zip")
    val resultStr            = result.body.string
    val templateId           = getTemplateId(resultStr)
    val assetsInBucket       = s3.listObjectsV2(assetsBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val templatesInBucket    = s3.listObjectsV2(templatesBucket).getObjectSummaries.asScala.map(_.getKey).toList
    val rawTemplatesInBucket = s3.listObjectsV2(rawTemplatesBucket).getObjectSummaries.asScala.map(_.getKey).toList

    val prefix = getPrefix(templateId, "1.0")
    assetsInBucket should contain(s"$prefix/email/assets/canary.png")
    templatesInBucket should contain allOf (s"$prefix/email/body.html", s"$prefix/email/subject.txt", s"$prefix/sms/body.txt")
    rawTemplatesInBucket should contain allOf (s"$prefix/email/assets/canary.png", s"$prefix/email/body.html", s"$prefix/email/subject.txt", s"$prefix/sms/body.txt")

    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    val templateVersionResult: TemplateVersion = templateVersions.find(_.commName == "TEST-COMM-PRINT").get
    templateVersionResult.version shouldBe "1.0"
    templateVersionResult.publishedBy shouldBe "dummy.email"
    templateVersionResult.commType shouldBe Service

    val templateSummaryResult = templateSummaries.find(_.commName == "TEST-COMM-PRINT").get
    templateSummaryResult.latestVersion shouldBe "1.0"
    templateSummaryResult.commType shouldBe Service

    resultStr should include(s"<p>Template ID: <strong>$templateId</strong></p>")
  }

  it should "reject new publication of invalid print templates with missing address field and script included" taggedAs DockerComposeTag in {
    val commName = "INVALID-PRINT-COMM"
    val fileName = "invalid-print-template.zip"

    val result            = createNewTemplate(commName, fileName)
    val templateSummaries = scan(templateSummaryTable)
    val templateVersions  = scan(templateVersionTable)

    templateVersions.find(_.commName == commName) shouldBe None
    templateSummaries.find(_.commName == commName) shouldBe None
    val resultBody = result.body().string()
    resultBody should include("<li>Missing expected address placeholder recipient.postalAddress.town</li>")
    resultBody should include("<li>Script included in print/body.html is not allowed</li>")
  }

  it should "return the previewed pdf from the composer" taggedAs DockerComposeTag in {

    val templateManifest = givenExistingTemplate()
    val testPdfBytes     = givenPrintPreview(templateManifest)

    val result = makeRequest(
      new Request.Builder()
        .url(s"http://localhost:9000/preview/${templateManifest.id}/${templateManifest.version}/print")
        .post(new FormBody.Builder().add("templateData", """{"foo":"bar"}""").build())
        .build()
    )

    result.isSuccessful shouldBe true
    result.header("Content-Type") shouldBe "application/pdf"
    ByteString(result.body().bytes()) shouldBe testPdfBytes
  }

  it should "return 404 when the template does not exist" taggedAs DockerComposeTag in {

    val templateManifest = givenNonExistingTemplate()
    givenPrintPreview(templateManifest)

    val result = makeRequest(
      new Request.Builder()
        .url(s"http://localhost:9000/preview/${templateManifest.id}/${templateManifest.version}/print")
        .post(new FormBody.Builder().add("templateData", """{"foo":"bar"}""").build())
        .build()
    )

    result.code() shouldBe 404
  }

  it should "return 404 when the composer return 404" taggedAs DockerComposeTag in {

    val templateManifest = givenExistingTemplate()
    givenPrintPreviewForNonExistingPrintChannel(templateManifest)

    val result = makeRequest(
      new Request.Builder()
        .url(s"http://localhost:9000/preview/${templateManifest.id}/${templateManifest.version}/print")
        .post(new FormBody.Builder().add("templateData", """{"foo":"bar"}""").build())
        .build()
    )

    result.code() shouldBe 404
  }

  private def givenExistingTemplate(): TemplateManifest = {
    val templateManifest = TemplateManifest(Hash("test-comm"), "12.0")
    val templateVersion =
      TemplateVersion(templateManifest, "test-comm", Service, "Phil", List[Channel]())
    Scanamo.put(dynamoClient)(templateVersionsTableName)(templateVersion)

    templateManifest
  }

  private def givenNonExistingTemplate(): TemplateManifest = {
    val templateManifest = TemplateManifest(Hash("test-comm"), "13.0")
    val templateVersion =
      TemplateVersion(templateManifest, "test-comm", Service, "Phil", List[Channel]())
    templateManifest
  }

  private def givenPrintPreview(templateManifest: TemplateManifest,
                                printPreviewBytes: ByteString = givenTestPdf): ByteString = {

    val testPdfAsBase64 = Base64.getEncoder.encodeToString(printPreviewBytes.toArray)

    mockServerClient
      .when(
        HttpRequest
          .request()
          .withMethod("POST")
          .withPath(s"/render/${templateManifest.id}/${templateManifest.version}/print")
      )
      .respond(
        HttpResponse
          .response()
          .withBody(s"""{"renderedPrint": "$testPdfAsBase64"}""")
      )

    printPreviewBytes
  }

  private def givenPrintPreviewForNonExistingPrintChannel(templateManifest: TemplateManifest): Unit = {

    mockServerClient
      .when(
        HttpRequest
          .request()
          .withMethod("POST")
          .withPath(s"/render/${templateManifest.id}/${templateManifest.version}/print")
      )
      .respond(
        HttpResponse
          .response()
          .withStatusCode(404)
          .withBody(s"""{"message": "Template not found"}""")
      )

  }

  private def givenTestPdf: ByteString = {
    val testPdfResource = getClass.getResource("/test.pdf")
    ByteString(Resources.toByteArray(testPdfResource))
  }

  private def scan[A](table: Table[A]): List[A] = {
    val query = table.scan()
    Scanamo.exec(dynamoClient)(query).flatMap(_.toOption)
  }

  private def makeRequest(request: Request) = httpClient.newCall(request).execute()

  private def getPrefix(templateId: String, version: String) =
    S3Prefix.fromTemplateManifest(TemplateManifest(templateId, version))

}
