package templates

import java.nio.file.Files

import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random

class ContentSpec extends FlatSpec with Matchers {

  val bigData: Seq[Byte]   = Seq.fill(Content.MaxInMemorySize + 1)(Random.nextInt.toByte)
  val smallData: Seq[Byte] = Seq.fill(Content.MaxInMemorySize)(Random.nextInt.toByte)

  "Content.apply from byte sequence" should "create a PathContent when the content size is bigger that MaxInMemorySize" in {
    Content(bigData) shouldBe a[PathContent]
  }

  "Content.apply from byte sequence" should "create a ByteArrayContent when the content size is smaller that MaxInMemorySize" in {
    Content(smallData) shouldBe a[ByteArrayContent]
  }

  "Content.apply from byte array" should "create a PathContent when the content size is bigger that MaxInMemorySize" in {
    Content(bigData.toArray) shouldBe a[PathContent]
  }

  "Content.apply from byte array" should "create a ByteArrayContent when the content size is smaller that MaxInMemorySize" in {
    Content(smallData.toArray) shouldBe a[ByteArrayContent]
  }

  "Content.apply from path" should "create a PathContent when the content size is bigger that MaxInMemorySize" in {
    val path = Files.createTempFile("ContentSpec", "tmp")
    Files.write(path, bigData.toArray)
    Content(path) shouldBe a[PathContent]
  }

  "Content.apply from path" should "create a ByteArrayContent when the content size is smaller that MaxInMemorySize" in {
    val path = Files.createTempFile("ContentSpec", "tmp")
    Files.write(path, smallData.toArray)
    Content(path) shouldBe a[ByteArrayContent]
  }

  "Content.map with a small content" should "keep the original Content unchanged" in {
    val original = Content("hello world")
    val mapped   = original.map(_.take(5))

    original.toUtf8 shouldBe "hello world"
    mapped.toUtf8 shouldBe "hello"
  }

  "Content.map with a big content" should "keep the original Content unchanged" in {
    val original = Content(bigData)
    val mapped   = original.map(_.take(5))

    original.toByteArray should contain theSameElementsInOrderAs bigData
    mapped.toByteArray should contain theSameElementsInOrderAs bigData.take(5)
  }

}
