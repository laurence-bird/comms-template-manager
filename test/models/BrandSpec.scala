package models

import models.Brand._
import org.scalatest.{FlatSpec, Matchers}

class BrandSpec extends FlatSpec with Matchers {


  "Brand values" should "contain each brand" in {
    Brand.values shouldEqual Seq(
      Ovo,
      Boost,
      Lumo,
      Corgi,
      Vnet
    )
  }


}
