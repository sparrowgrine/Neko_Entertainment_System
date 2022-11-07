package ee.catgirl.nes.mapper

import chisel3._
import ee.catgirl.nes.util.{AsyncROM, ROMInfo}
import org.scalatest.Assertions._

class NROM(romInfo : ROMInfo, romData : Array[Byte]) extends Mapper(romInfo, romData) {
  val prgRomStart = 16 + (if(romInfo.hasTrainer) 512 else 0)
  val prgRomEnd = prgRomStart+romInfo.prgROMSize*16384
  val prgRomData = romData.slice(prgRomStart,prgRomEnd).map(b => if (b < 0) BigInt(b+256) else BigInt(b))
  val prgRom = Module(new AsyncROM("cpu_rom",prgRomData.toSeq,Some(8)))

  val chrRomStart = prgRomEnd
  val chrRomEnd = chrRomStart + romInfo.chrROMSize*8192
  val chrRomData = if(chrRomStart == chrRomEnd) {
    Array.fill(8192)(BigInt(0))
  } else {
    romData.slice(chrRomStart, chrRomEnd).map(b => if (b < 0) BigInt(b + 256) else BigInt(b))
  }
  val chrRom = Module(new AsyncROM("ppu_rom", chrRomData.toSeq,Some(8)))

  val prgRam = Mem(8192,UInt(8.W))
  val sysRam = Mem(2048,UInt(8.W))
  val prgRamPort = prgRam(io.cpuAB(12,0))
  val sysRamPort = sysRam(io.cpuAB(10,0))

  val cpuAddrBits = romInfo.prgROMSize match {
    case 1 => 14
    case 2 => 15
    case _ => fail("Unsupported ROM Size for NROM Mapper.")
  }

  val dataReg = RegInit(0.U(8.W))
  io.cpuDO := dataReg
  io.cpuRDY := true.B

  prgRom.io.addr := io.cpuAB(cpuAddrBits - 1, 0)

  val sysRamEn = io.cpuAB < 0x2000.U
  val prgRamEn = io.cpuAB >= 0x6000.U && io.cpuAB <= 0x7FFF.U
  val prgRomEn = io.cpuAB >= 0x8000.U

  when(sysRamEn) {
    when(io.cpuWE) {
      sysRamPort := io.cpuDI
    }
    .otherwise {
      dataReg := sysRamPort
    }
  }
  .elsewhen(prgRomEn) {
      when(!io.cpuWE) {
        dataReg := prgRom.io.data
      }
  }
  .elsewhen(prgRamEn) {
    when(io.cpuWE) {
      prgRamPort := io.cpuDI
    }
    .otherwise {
      dataReg := prgRamPort
    }
  }

  //TODO: PPU Support
  chrRom.io.addr := io.ppuAB
  io.ppuDO := chrRom.io.data
  io.cpuIRQ := false.B

}
