package models

import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}

class TemplateSummaryOpsSpec extends FlatSpec with Matchers {

  behavior of "TemplateSummary"

  it should "compare versions correctly" in {
    TemplateSummaryOps.versionCompare("1.12", "1.3") shouldBe Right(1)
    TemplateSummaryOps.versionCompare("1.2", "1.12") shouldBe Right(-1)
    TemplateSummaryOps.versionCompare("1.12.1", "1.12") shouldBe Right(1)
    TemplateSummaryOps.versionCompare("1.12", "1.12.2") shouldBe Right(-1)
    TemplateSummaryOps.versionCompare("1.12", "1.3.1") shouldBe Right(1)
    TemplateSummaryOps.versionCompare("1.2", "1.1.1") shouldBe Right(1)
    TemplateSummaryOps.versionCompare("1.0", "1.0.1") shouldBe Right(-1)
    TemplateSummaryOps.versionCompare("1.0", "1.0") shouldBe Right(0)
    TemplateSummaryOps.versionCompare("1.0.1", "1.0.1") shouldBe Right(0)
    TemplateSummaryOps.versionCompare("1.0", "0.2") shouldBe Right(1)
    TemplateSummaryOps.versionCompare("1.0.0", "1.1") shouldBe Right(-1)
  }

  it should "handle invalid version when comparing" in {
    TemplateSummaryOps.versionCompare("1.12b", "1.3").isLeft shouldBe true
    TemplateSummaryOps.versionCompare("1.12", "1.x").isLeft shouldBe true
  }

  it should "determine new version correctly" in {
    TemplateSummaryOps.nextVersion("2.4.3") shouldBe Right("3.0.0")
    TemplateSummaryOps.nextVersion("1.0.0") shouldBe Right("2.0.0")
    TemplateSummaryOps.nextVersion("4.3") shouldBe Right("5.0")
    TemplateSummaryOps.nextVersion("2.0") shouldBe Right("3.0")
    TemplateSummaryOps.nextVersion("5") shouldBe Right("6")
  }

  it should "handle invalid version when determining new version" in {
    TemplateSummaryOps.nextVersion("1.12b").isLeft shouldBe true
    TemplateSummaryOps.nextVersion("x").isLeft shouldBe true
  }

}
