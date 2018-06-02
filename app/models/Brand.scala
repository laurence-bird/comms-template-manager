//package models
//
//import enumeratum._
//import scala.collection.immutable.IndexedSeq
//
//sealed trait Brand extends EnumEntry {
//  val value: String
//  val displayValue: String
//}
//
//object Brand extends Enum[Brand] {
//  case object Ovo extends Brand {
//    override val value        = "ovo"
//    override val displayValue = "OVO"
//  }
//
//  case object Boost extends Brand {
//    override val value        = "boost"
//    override val displayValue = "Boost"
//  }
//
//  case object Lumo extends Brand {
//    override val value        = "lumo"
//    override val displayValue = "Lumo"
//  }
//
//  case object Corgi extends Brand {
//    override val value        = "corgi"
//    override val displayValue = "Corgi"
//  }
//
//  case object Vnet extends Brand {
//    override val value        = "vnet"
//    override val displayValue = "VNet"
//  }
//
//  /*
//  To use for development, testing. Not to be displayed on UI.
//   */
//  case object Unbranded extends Brand {
//    override val value        = "unbranded"
//    override val displayValue = "Unbranded"
//  }
//
//  override def values: IndexedSeq[Brand] = findValues
//
//  def fromString(status: String): Option[Brand] = Brand.withNameInsensitiveOption(status)
//}
