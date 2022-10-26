package ee.catgirl.nes.mapper

import chisel3._
import chisel3.experimental.dataview.DataView
import chisel3.util._
import ee.catgirl.nes.util.{AsyncROM, ROMInfo}
import org.scalatest.Assertions._

private class MMC1Shreg extends Module with RequireAsyncReset {
  val io = IO(new Bundle {
    val in = Input(Bool())
    val clear = Input(Bool())
    val state = Output(UInt(4.W))
    val en = Input(Bool())
  })

  val regs = RegInit(0.U(4.W))
  when(io.clear) {
    regs := Fill(4,0.B)
  }
  .elsewhen(io.en) {
    regs := Cat(regs(2),regs(1),regs(0),io.in)
  }
  io.state := regs
}

private class MMC1Emulator extends Module with RequireAsyncReset {
  val io = IO(new Bundle {
    val cpuAB = Input(UInt(16.W))
    val cpuDI = Input(UInt(8.W))
    val cpuWE = Input(Bool())
    val prgRomBank0 = Output(UInt(4.W))
    val prgRomBank1 = Output(UInt(4.W))
    val chrRomBank0 = Output(UInt(5.W))
    val chrRomBank1 = Output(UInt(5.W))
  })

  class ControlRegDef extends Bundle {
    val chrBankMode = Bool()
    val prgBankMode = UInt(2.W)
    val mirrorMode = UInt(2.W)
  }

  class PRGBankRegDef extends Bundle {
    val prgRamCE = Bool()
    val prgBankAddr = UInt(4.W)
  }

  val writeCounter = RegInit(0.U(3.W))
  val controlReg = RegInit("b01100".U.asTypeOf(new ControlRegDef))
  val chrBank0Reg = RegInit(0.U(5.W))
  val chrBank1Reg = RegInit(0.U(5.W))
  val prgBankReg = RegInit(0.U.asTypeOf(new PRGBankRegDef))
  val lastWE = RegNext(io.cpuWE,0.B)
  val wePosEdge = WireDefault((io.cpuWE ^ lastWE) & io.cpuWE)
  val shreg = Module(new MMC1Shreg())
  val shregEn = io.cpuAB(15) & wePosEdge
  val resetMMC1 = (io.cpuAB(15) & io.cpuWE & io.cpuDI(7))
  val shregClear = resetMMC1 | (writeCounter === 5.U)
  val stateUpdate = Cat(shreg.io.state,io.cpuDI(0))
  shreg.io.in := io.cpuDI(0)
  shreg.io.clear := shregClear
  shreg.io.en := shregEn


  when(shregClear) {
    writeCounter := 0.U
  }
  .elsewhen(shregEn) {
    writeCounter := writeCounter + 1.U
  }

  when(resetMMC1) {
    controlReg := (controlReg.asUInt | 0xC.U).asTypeOf(controlReg)
  }

  when((writeCounter === 4.U) & shregEn) {
    printf("Mapper update!\n")
    switch(io.cpuAB(14,13)) {
      is(0.U) {
        printf("Updating controlReg to %b\n",stateUpdate)
        controlReg := stateUpdate.asTypeOf(controlReg)
      }
      is(1.U) {
        printf("Updating chrBank0Reg to %b\n",stateUpdate)
        chrBank0Reg := stateUpdate
      }
      is(2.U) {
        printf("Updating chrBank1Reg to %b\n",stateUpdate)
        chrBank1Reg := stateUpdate
      }
      is(3.U) {
        printf("Updating prgBankReg to %b\n",stateUpdate)
        prgBankReg := stateUpdate.asTypeOf(prgBankReg)
      }
    }
  }

  io.chrRomBank0 := Mux(controlReg.chrBankMode,chrBank0Reg >> 1, chrBank0Reg)
  io.chrRomBank1 := Mux(controlReg.chrBankMode,(chrBank0Reg >> 1.U) + 1.U, chrBank1Reg)
  when (controlReg.prgBankMode ===0.U | controlReg.prgBankMode ===1.U ) {
    io.prgRomBank0 := prgBankReg.prgBankAddr
    io.prgRomBank1 := prgBankReg.prgBankAddr + 1.U
  }
  .elsewhen(controlReg.prgBankMode === 2.U) {
    io.prgRomBank0 := 0.U
    io.prgRomBank1 := prgBankReg.prgBankAddr
  }
  .elsewhen(controlReg.prgBankMode === 3.U) {
    io.prgRomBank0 := prgBankReg.prgBankAddr
    io.prgRomBank1 := "b1111".U
  }
  .otherwise {
    io.prgRomBank0 := 0.U
    io.prgRomBank1 := 0.U
  }
}

class MMC1(romInfo : ROMInfo, romData : Array[Byte]) extends Mapper(romInfo, romData)  {
  val prgRomStart = 16 + (if (romInfo.hasTrainer) 512 else 0)
  val prgRomEnd = prgRomStart + romInfo.prgROMSize * 16384
  val prgRomData = romData.slice(prgRomStart, prgRomEnd).map(b => if (b < 0) BigInt(b + 256) else BigInt(b))
  val prgRom = Module(new AsyncROM("cpu_rom", prgRomData.toSeq))

  val chrRomStart = prgRomEnd
  var chrRomEnd = chrRomStart + romInfo.chrROMSize * 8192

  val chrRomData = if(romInfo.chrROMSize != 0) {
    romData.slice(chrRomStart, chrRomEnd).map(b => if (b < 0) BigInt(b + 256) else BigInt(b)).toSeq
  } else {
    chrRomEnd += 8192
    Seq.fill(8192) {BigInt(0)}
  }
  val chrRom = Module(new AsyncROM("ppu_rom", chrRomData))

  val prgRam = Mem(8192, UInt(8.W))
  val sysRam = Mem(2048, UInt(8.W))
  val prgRamPort = prgRam(io.cpuAB(12, 0))
  val sysRamPort = sysRam(io.cpuAB(10, 0))


  private val mmc1 = Module(new MMC1Emulator)
  mmc1.io.cpuAB := io.cpuAB
  mmc1.io.cpuDI := io.cpuDI
  mmc1.io.cpuWE := io.cpuWE

  val dataReg = RegInit(0.U(8.W))
  io.cpuDO := dataReg
  io.cpuRDY := true.B

  val prgRomAddr = Mux(io.cpuAB(14), Cat(mmc1.io.prgRomBank1,io.cpuAB(13, 0)),Cat(mmc1.io.prgRomBank0,io.cpuAB(13, 0)))
  prgRom.io.addr := prgRomAddr


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
  val chrRomAddr = Mux(io.ppuAB(13), Cat(mmc1.io.prgRomBank1,io.ppuAB(12, 0)),Cat(mmc1.io.prgRomBank0,io.ppuAB(13, 0)))
  chrRom.io.addr := chrRomAddr
  io.ppuDO := chrRom.io.data

}
