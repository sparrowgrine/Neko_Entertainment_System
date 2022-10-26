package ee.catgirl.nes.util

import ee.catgirl.nes.util.MirrorTypes.MirrorType

object MirrorTypes extends Enumeration {
  type MirrorType = Value
  val Vertical, Horizontal, Ignored = Value
  def valueForOptionByte(b : Byte) = {
    if (((b >> 3) & 1) == 1) {
      MirrorTypes.Ignored
    } else {
      if ((b & 1) == 1) {
        MirrorTypes.Vertical
      } else {
        MirrorTypes.Horizontal
      }
    }
  }
}

object ROMInfo {
  def apply(romName: String,romdata : Array[Byte]): ROMInfo = {
    val prgRomSize = romdata(4)
    val chrRomSize = romdata(5)
    val mirroringType = MirrorTypes.valueForOptionByte(romdata(6))
    val hasPrgRam = ((romdata(6) >> 1) & 1) == 1
    val prgRamSize = if (romdata(8) == 0) (romdata(6) >> 1) & 1 else romdata(8)
    val hasTrainer = ((romdata(6) >> 2) & 1) == 1
    val mapperNumber = (romdata(6) >> 4) | (romdata(7) & 0xF0)

    ROMInfo(romName,prgRomSize,chrRomSize,mirroringType,hasPrgRam,prgRamSize,hasTrainer,mapperNumber)
  }
}

case class ROMInfo(name: String, prgROMSize : Int, chrROMSize: Int, mirroringType : MirrorType, hasPRGRAM : Boolean, prgRAMSize : Int, hasTrainer : Boolean, mapperNumber : Int )

