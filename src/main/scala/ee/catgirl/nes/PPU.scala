package ee.catgirl.nes

import chisel3._

class PPU extends Module {
  val io = IO(new Bundle{
    val cpuAddr = Input(UInt(3.W))
    val cpuData = Input(UInt(8.W))
    val cs = Input(Bool())
    val int = Output(Bool())
    val ale = Output(Bool())
    val ppuAddr = Output(UInt(14.W))
    val ppuDataIn = Input(UInt(8.W))
    val ppuDataOut = Output(UInt(8.W))
    val rd = Output(Bool())
    val wr = Output(Bool())
    val extIn = Input(UInt(4.W))
    val extOut = Input(UInt(4.W))
    val rw = Input(Bool())
    val videoR = Output(Bool())
    val videoG = Output(Bool())
    val videoB = Output(Bool())
  })
  //TODO: Rest of the fucking PPU
}
