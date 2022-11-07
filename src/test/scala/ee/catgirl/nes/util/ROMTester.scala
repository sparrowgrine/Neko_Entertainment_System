package ee.catgirl.nes.util

import chisel3._
import chisel3.testers.BasicTester
import ee.catgirl.nes.{CPU, DMAController, NESTop}
import ee.catgirl.nes.apu.APU
import ee.catgirl.nes.mapper.{MAPPERS, Mapper}

import java.io.{BufferedInputStream, FileInputStream}
import java.lang.reflect.Constructor
import scala.language.postfixOps
import scala.reflect.ClassTag


class ROMTester(val romPath : String) extends BasicTester with RequireSyncReset {

  val romstream = new BufferedInputStream(new FileInputStream(romPath))
  val romdata: Array[Byte] = LazyList.continually(romstream.read).takeWhile(-1 !=).map(_.toByte).toArray
  assert(romdata(0) == 'N'.toByte && romdata(1) == 'E'.toByte && romdata(2) == 'S'.toByte && romdata(3) == 0x1A)

  val romName = romPath.substring(romPath.lastIndexOf('/') + 1)
  val romInfo = ROMInfo(romName, romdata)

  val printReg = RegInit(1.B)
  when(printReg) {
    printf(s"Starting execution of ${romInfo.name}!\n")
    printf(s"Rom Info:\n")
    printf(s"${romInfo.prgROMSize} PRG ROM Banks, ${romInfo.prgROMSize * 16384} bytes\n")
    printf(s"${romInfo.chrROMSize} CHR ROM Banks, ${romInfo.chrROMSize * 8192} bytes\n")
    printf(s"CHR ROM is using ${romInfo.mirroringType} mirroring.\n")
    if (romInfo.hasPRGRAM) {
      printf(s"${romInfo.prgRAMSize} PRG RAM Banks, ${romInfo.prgRAMSize * 8192} bytes\n")
    }
    printf(s"Mapper ${romInfo.mapperNumber} is to be used.\n")
    printReg := 0.B
  }

  if (!MAPPERS.contains(romInfo.mapperNumber)) {
    println(s"Unimplemented mapper ${romInfo.mapperNumber} requested!")
  }

  val mapperClass: Class[_ <: Mapper] = MAPPERS(romInfo.mapperNumber)
  val ctor: Constructor[_ <: Mapper] = mapperClass.getDeclaredConstructor(classOf[ROMInfo],ClassTag(classOf[Byte]).wrap.runtimeClass)
  val mapper: Mapper = Module(ctor.newInstance(romInfo,romdata))

  val testerReset = RegInit(0.B)

  when(testerReset === 1.B) {
    testerReset := 0.B
  }

  val failGeneric = RegInit(0.B)
  val failSpecific = RegInit(0.B)

  assert(failGeneric === 0.B, "Failure encountered in test! :(")
  assert(failSpecific === 0.B, "Failure encountered in test! :(")

  val startReg = RegInit(0.B)
  val lastPrintAddr = RegInit(0x6003.U(16.W))

  val debugPrintBegin = RegInit(VecInit.fill(3) {
    0.B
  })
  val debugPrintReg = RegInit(0.B)

  withReset((testerReset | reset.asBool)) {

    val nes = Module(new NESTop)

    mapper.io.cpuAB := nes.io.cpuBusAddr
    mapper.io.cpuDI := nes.io.cpuBusDataOut
    mapper.io.cpuWE := nes.io.cpuBusWE
    nes.io.cpuBusDataIn := mapper.io.cpuDO
    nes.io.cpuExtAccessRdy := mapper.io.cpuRDY
    nes.io.extIrq := mapper.io.cpuIRQ


    when(!debugPrintBegin.asUInt.andR) {
      when((nes.io.cpuBusDataOut === 0xDE.U) & (nes.io.cpuBusAddr === 0x6001.U) & nes.io.cpuBusWE) {
        debugPrintBegin(0) := 1.B
      }
      when((nes.io.cpuBusDataOut === 0xB0.U) & (nes.io.cpuBusAddr === 0x6002.U) & nes.io.cpuBusWE) {
        debugPrintBegin(1) := 1.B
      }
      when((nes.io.cpuBusDataOut === 0x61.U) & (nes.io.cpuBusAddr === 0x6003.U) & nes.io.cpuBusWE) {
        debugPrintBegin(2) := 1.B
      }
    }


    when(debugPrintBegin.asUInt.andR & ~debugPrintReg) {
      printf("inst_test detected! Beginning Debug Output\n")
      debugPrintReg := 1.B
    }

    val cycleCounter = RegInit(0.U(32.W))
    cycleCounter := cycleCounter + 1.U
    when(nes.io.apuSample =/= RegNext(nes.io.apuSample)) {
      printf("APU_SAMPLE: %x / %x\n",cycleCounter,nes.io.apuSample)
    }

    when(~startReg & (nes.io.cpuBusDataOut === 0x80.U) & (nes.io.cpuBusAddr === 0x6000.U) & nes.io.cpuBusWE) {
      startReg := 1.B
      printf("Test Started!!\n")
    }

    when(startReg & ((nes.io.cpuBusAddr === 0x6000.U) & (nes.io.cpuBusDataOut <= 0x7F.U) & nes.io.cpuBusWE) & ~failGeneric & ~failSpecific) {
      when(nes.io.cpuBusDataOut === 0.U) {
        stop()
      }
        .elsewhen(nes.io.cpuBusDataOut === 1.U) {
          printf("Generic Failure Encountered In Test.\n")
          failGeneric := 1.B
        }
        .elsewhen(nes.io.cpuBusDataOut >= 2.U) {
          printf("Failure 0x%x Encountered In Test! (Check Source of Test To Find Meaning.)\n", nes.io.cpuBusDataOut)
          failSpecific := 1.B
        }
    }
    when(startReg & ((nes.io.cpuBusAddr === 0x6000.U) & (nes.io.cpuBusDataOut === 0x81.U) & nes.io.cpuBusWE) & ~failGeneric & ~failSpecific) {
      printf("Issuing Reset From Harness!\n")
      testerReset := 1.B
    }
    when(debugPrintBegin.asUInt.andR & (nes.io.cpuBusAddr >= 0x6004.U) & (nes.io.cpuBusAddr < 0x6FFF.U) & nes.io.cpuBusDataOut =/= 0x0.U) {
      when((lastPrintAddr) =/= nes.io.cpuBusAddr) {
        lastPrintAddr := nes.io.cpuBusAddr
        printf("%c", nes.io.cpuBusDataOut)
      }
    }

    when((nes.io.apuSample =/= RegNext(nes.io.apuSample)) & false.B) {
      printf("apuSample: %x\n", nes.io.apuSample)
    }
  }

  //TODO: PPU Support
  mapper.io.ppuAB := 0.U
  mapper.io.ppuDI := 0.U
  mapper.io.ppuRD := 0.B
  mapper.io.ppuWR := 0.B

}
