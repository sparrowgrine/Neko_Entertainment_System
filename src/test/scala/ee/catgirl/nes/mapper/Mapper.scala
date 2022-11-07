package ee.catgirl.nes.mapper

import chisel3._
import ee.catgirl.nes.util.ROMInfo

abstract class Mapper(val romInfo : ROMInfo, val romData : Array[Byte]) extends Module {
  val io = IO(new Bundle{
    val cpuAB = Input(UInt(16.W))
    val cpuDI = Input(UInt(8.W))
    val cpuDO = Output(UInt(8.W))
    val cpuWE = Input(Bool())
    val cpuRDY = Output(Bool())
    val cpuIRQ = Output(Bool())
    val ppuAB = Input(UInt(16.W))
    val ppuDI = Input(UInt(8.W))
    val ppuDO = Output(UInt(8.W))
    val ppuWR = Input(Bool())
    val ppuRD = Input(Bool())
  })
}
