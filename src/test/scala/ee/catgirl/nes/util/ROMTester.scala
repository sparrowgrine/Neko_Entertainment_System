package ee.catgirl.nes.util

import chisel3._
import chisel3.testers.BasicTester
import ee.catgirl.nes.CPU
import ee.catgirl.nes.mapper.{MAPPERS, Mapper}

import java.io.{BufferedInputStream, FileInputStream}
import java.lang.reflect.Constructor
import scala.language.postfixOps
import scala.reflect.ClassTag


class ROMTester(val romPath : String) extends BasicTester with RequireAsyncReset {

  val cpu = Module(new CPU())
  val romstream = new BufferedInputStream(new FileInputStream(romPath))
  val romdata: Array[Byte] = LazyList.continually(romstream.read).takeWhile(-1 !=).map(_.toByte).toArray
  assert(romdata(0) == 'N'.toByte && romdata(1) == 'E'.toByte && romdata(2) == 'S'.toByte && romdata(3) == 0x1A)

  val romName = romPath.substring(romPath.lastIndexOf('/') + 1)
  val romInfo = ROMInfo(romName,romdata)

  val printReg = RegInit(1.B)
  when(printReg) {
    printf(s"Starting execution of ${romInfo.name}!\n")
    printf(s"Rom Info:\n")
    printf(s"${romInfo.prgROMSize} PRG ROM Banks, ${romInfo.prgROMSize*16384} bytes\n")
    printf(s"${romInfo.chrROMSize} CHR ROM Banks, ${romInfo.chrROMSize*8192} bytes\n")
    printf(s"CHR ROM is using ${romInfo.mirroringType} mirroring.\n")
    if(romInfo.hasPRGRAM) {
      printf(s"${romInfo.prgRAMSize} PRG RAM Banks, ${romInfo.prgRAMSize*8192} bytes\n")
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

  mapper.io.cpuAB := cpu.io.AB
  mapper.io.cpuDI := cpu.io.DO
  mapper.io.cpuWE := cpu.io.WE
  cpu.io.DI := mapper.io.cpuDO
  cpu.io.RDY := mapper.io.cpuRDY
  cpu.io.IRQ := false.B
  cpu.io.NMI := false.B

  val failGeneric = RegInit(0.B)
  val failSpecific = RegInit(0.B)

  assert(failGeneric === 0.B, "Failure encountered in test! :(")
  assert(failSpecific === 0.B, "Failure encountered in test! :(")

  val startReg = RegInit(0.B)
  val lastPrintAddr = RegInit(0x6003.U(16.W))

  val debugPrintBegin = RegInit(VecInit.fill(3) { 0.B })
  val debugPrintReg = RegInit(0.B)

  when(!debugPrintBegin.asUInt.andR) {
    when((cpu.io.DO === 0xDE.U) & (cpu.io.AB === 0x6001.U) & cpu.io.WE) {
      debugPrintBegin(0) := 1.B
    }
    when((cpu.io.DO === 0xB0.U) & (cpu.io.AB === 0x6002.U) & cpu.io.WE) {
      debugPrintBegin(1) := 1.B
    }
    when((cpu.io.DO === 0x61.U) & (cpu.io.AB === 0x6003.U) & cpu.io.WE) {
      debugPrintBegin(2) := 1.B
    }
  }

  when(debugPrintBegin.asUInt.andR & ~debugPrintReg) {
    printf("inst_test detected! Beginning Debug Output\n")
    debugPrintReg := 1.B
  }


  when(~startReg & (cpu.io.DO === 0x80.U) & (cpu.io.AB === 0x6000.U) & cpu.io.WE) {
    startReg := 1.B
    printf("Test Started!!\n")
  }

  when(startReg & ((cpu.io.AB === 0x6000.U) & (cpu.io.DO <= 0x7F.U) & cpu.io.WE) & ~failGeneric & ~failSpecific) {
    when(cpu.io.DO === 0.U) {
      stop()
    }
    .elsewhen(cpu.io.DO === 1.U) {
      printf("Generic Failure Encountered In Test.\n")
      failGeneric := 1.B
    }
    .elsewhen(cpu.io.DO >= 2.U) {
      printf("Failure 0x%x Encountered In Test! (Check Source of Test To Find Meaning.)\n",cpu.io.DO)
      failSpecific := 1.B
    }
  }
  when(startReg & ((cpu.io.AB === 0x6000.U) & (cpu.io.DO === 0x81.U) & cpu.io.WE) & ~failGeneric & ~failSpecific) {
    printf("Oh God Oh Fuck Reset Needed!!!")
  }
    when(debugPrintBegin.asUInt.andR & (cpu.io.AB >= 0x6004.U ) & (cpu.io.AB < 0x6FFF.U ) & cpu.io.DO =/= 0x0.U) {
    when((lastPrintAddr ) =/= cpu.io.AB) {
      lastPrintAddr := cpu.io.AB
      printf("%c", cpu.io.DO)
    }
  }

  //TODO: PPU Support
  mapper.io.ppuAB := 0.U
  mapper.io.ppuDI := 0.U
  mapper.io.ppuRD := 0.B
  mapper.io.ppuWR := 0.B

}
