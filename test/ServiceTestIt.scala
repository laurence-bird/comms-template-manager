import java.awt.PageAttributes.MediaType
import java.io.{ByteArrayInputStream, IOException}
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream}

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3Client, S3ClientOptions}
import com.ovoenergy.comms.model.{CommManifest, CommType}
import com.typesafe.config.{ConfigFactory, ConfigParseOptions, ConfigResolveOptions}
import okhttp3.{OkHttpClient, Request}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, Tag}

class ServiceTestIt extends FlatSpec with Matchers with BeforeAndAfterAll {

  object DockerComposeTag extends Tag("DockerComposeTag")

  val config =
    ConfigFactory.load(ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))

  val s3Endpoint = "http://localhost:4569"
  val bucketName = config.getString("aws.s3.buckets.rawTemplates")

  override def beforeAll() = {
    initialiseS3Bucket()
  }

  private def initialiseS3Bucket() = {
    val s3clientOptions = S3ClientOptions.builder().setPathStyleAccess(true).disableChunkedEncoding().build()

    val s3: AmazonS3Client = new AmazonS3Client(new BasicAWSCredentials("key", "secret"))
      .withRegion(Regions.fromName("eu-west-1"))

    s3.setS3ClientOptions(s3clientOptions)
    s3.setEndpoint(s3Endpoint)
    s3.createBucket(bucketName)

    s3.putObject(bucketName,
      "service/template-manager-service-test/0.1/email/subject.txt",
      "SUBJECT {{profile.firstName}}")
    s3.putObject(bucketName,
      "service/template-manager-service-test/0.1/email/body.html",
      "{{> header}} HTML BODY {{amount}}")
    s3.putObject(bucketName,
      "service/template-manager-service-test/0.1/email/body.txt",
      "{{> header}} TEXT BODY {{amount}}")

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
      try{
        val entry: ZipEntry = is.getNextEntry
        getFileNames(is, fileNames :+ entry.getName)
      } catch{
        case e: Throwable  => fileNames
      }
    }

    val fileNames = getFileNames(zipFileStream, Nil)

    fileNames should contain allOf("/email/body.html", "/email/body.txt", "/email/subject.txt")
  }

  private def makeRequest(request: Request) = {
    val client = new OkHttpClient()
    client.newBuilder().connectTimeout(2, TimeUnit.SECONDS)
    client.newCall(request).execute()
  }

}
