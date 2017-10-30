package templates.validation

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import templates.validation.HtmlContentParser.browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model._

object HtmlContentParser {
  val browser = JsoupBrowser()

  def getDocument(documentStr: String) = {
    browser.parseString(documentStr)
  }

  def getElementWithId(htmlStr: String, id: String): Option[Element] = {
    getDocument(htmlStr) >?> element(s"#$id")
  }

  def getElementsWithId(htmlStr: String, id: String): Option[List[Element]] = {
    getDocument(htmlStr) >?> elementList(s"#$id")
  }

  def getElement(htmlStr: String, id: String): Option[Element] = {
    getDocument(htmlStr) >?> element(id)
  }

  def getElements(htmlStr: String, id: String): Option[List[Element]] = {
    getDocument(htmlStr) >?> elementList(id)
  }

  implicit class ElementExtensions(element: Element) {
    def contains(expectedValue: String) = {
      element.text.contains(expectedValue)
    }
  }

}
