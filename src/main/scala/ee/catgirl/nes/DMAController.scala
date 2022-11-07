package ee.catgirl.nes

import chisel3._
import chisel3.util._

class DMAController extends Module{
  val io = IO(new Bundle {
    val CE = Input(Bool())
    val oddCycle = Input(Bool())
    val dmcInitiated = Input(Bool())
    val spriteInitiated = Input(Bool())
    val cpuInReadCycle = Input(Bool())
    val dataFromCPU = Input(UInt(8.W))
    val dataFromMem = Input(UInt(8.W))
    val dmcDmaAddr = Input(UInt(16.W))
    val addressOut = Output(UInt(16.W))
    val busRequest = Output(Bool())
    val isRead = Output(Bool())
    val dataToMem = Output(UInt(8.W))
    val dmcAck = Output(Bool())
    val cpuHalt = Output(Bool())
  })

  val dmcState = RegInit(0.B)
  val spriteState = RegInit(0.U(2.W))
  val prevSpriteDmaVal = RegInit(0.U(8.W))
  val spriteDmaAddrHigh = RegInit(0.U(8.W))
  val spriteDmaAddrLow = RegInit(0.U(8.W))
  val nextSpriteDmaAddr = spriteDmaAddrLow(7,0) +& 1.U

  io.cpuHalt := (spriteState(0) | io.dmcInitiated) & io.cpuInReadCycle
  io.dmcAck := (dmcState === 1.U) & !io.oddCycle
  io.busRequest := io.dmcAck | spriteState(1)
  io.isRead := ~io.oddCycle
  io.dataToMem := prevSpriteDmaVal
  io.addressOut := Mux(io.dmcAck, io.dmcDmaAddr, Mux(!io.oddCycle,Cat(spriteDmaAddrHigh,spriteDmaAddrLow),0x2004.U))

  when(io.CE) {
    when(!dmcState & io.dmcInitiated & io.cpuInReadCycle & ~io.oddCycle) {
      dmcState := 1.B
    }
    when(dmcState & ~io.oddCycle) {
      dmcState := 0.B
    }
    when(io.spriteInitiated) {
      spriteDmaAddrHigh := io.dataFromCPU
      spriteState := 1.U
    }
    when(spriteState === 1.U & io.cpuInReadCycle & io.oddCycle) {
      spriteState := 3.U
    }
    when(spriteState(1) & !io.oddCycle & dmcState) {
      spriteState := 1.U
    }
    when(spriteState(1) & io.oddCycle) {
      spriteDmaAddrLow := nextSpriteDmaAddr
    }
    when(spriteState(1) & io.oddCycle & nextSpriteDmaAddr(8)) {
      spriteState := 0.U
    }
    when(spriteState(1)) {
      prevSpriteDmaVal := io.dataFromMem
    }
  }
}
