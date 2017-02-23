package models

import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}

class TemplateSummarySpec extends FlatSpec with Matchers {

  behavior of "TemplateSummary"

  it should "compare versions correctly" in {
    TemplateSummary.versionCompare("1.12", "1.3") shouldBe Right(1)
    TemplateSummary.versionCompare("1.2", "1.12") shouldBe Right(-1)
    TemplateSummary.versionCompare("1.12.1", "1.12") shouldBe Right(1)
    TemplateSummary.versionCompare("1.12", "1.12.2") shouldBe Right(-1)
    TemplateSummary.versionCompare("1.12", "1.3.1") shouldBe Right(1)
    TemplateSummary.versionCompare("1.2", "1.1.1") shouldBe Right(1)
    TemplateSummary.versionCompare("1.0", "1.0.1") shouldBe Right(-1)
    TemplateSummary.versionCompare("1.0", "1.0") shouldBe Right(0)
    TemplateSummary.versionCompare("1.0.1", "1.0.1") shouldBe Right(0)
  }

  it should "handle invalid version when comparing" in {
    TemplateSummary.versionCompare("1.12b", "1.3").isLeft shouldBe true
    TemplateSummary.versionCompare("1.12", "1.x").isLeft shouldBe true
  }

  it should "determine new version correctly" in {
    TemplateSummary.nextVersion("2.4.3") shouldBe Right("3.0.0")
    TemplateSummary.nextVersion("1.0.0") shouldBe Right("2.0.0")
    TemplateSummary.nextVersion("4.3") shouldBe Right("5.0")
    TemplateSummary.nextVersion("2.0") shouldBe Right("3.0")
    TemplateSummary.nextVersion("5") shouldBe Right("6")
  }

  it should "handle invalid version when determining new version" in {
    TemplateSummary.nextVersion("1.12b").isLeft shouldBe true
    TemplateSummary.nextVersion("x").isLeft shouldBe true
  }



}
