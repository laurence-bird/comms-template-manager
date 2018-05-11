package views

import java.util.UUID

import com.ovoenergy.comms.model._
import models.TemplateVersionLegacy
import org.scalatest.{FlatSpec, Matchers}

class ViewHelperSpec extends FlatSpec with Matchers {

  def randomString() = UUID.randomUUID().toString

  def buildTemplateVersion(channel: List[Channel]) = {
    TemplateVersion(CommManifest(Service, randomString(), randomString()), randomString(), channel)
  }

  behavior of "ViewHelper"

  it should "Determine whether a template supports print" in {
    ViewHelper.containsPrintTemplate(buildTemplateVersion(List(Print))) shouldBe true
    ViewHelper.containsPrintTemplate(buildTemplateVersion(List(Print, Email, SMS))) shouldBe true
    ViewHelper.containsPrintTemplate(buildTemplateVersion(List(Print, SMS))) shouldBe true
    ViewHelper.containsPrintTemplate(buildTemplateVersion(List(SMS))) shouldBe false
    ViewHelper.containsPrintTemplate(buildTemplateVersion(List(Email, SMS))) shouldBe false
  }
}
