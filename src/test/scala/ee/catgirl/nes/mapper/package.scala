package ee.catgirl.nes


package object mapper {
  val MAPPERS : Map[Int,Class[_ <: Mapper]] = Map(
    0 -> classOf[NROM],
    1 -> classOf[MMC1]
  )
}
