package ee.catgirl.nes.apu

import chisel3._
import chisel3.util._

class DMCChannel extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(3.W))
    val dataIn = Input(UInt(8.W))
    val CE = Input(Bool())
    val WE = Input(Bool())
    val dmaData = Input(UInt(8.W))
    val odd_even = Input(Bool())
    val dmaReq = Output(Bool())
    val dmaAck = Input(Bool())
    val dmaAddr = Output(UInt(16.W))
    val irq = Output(Bool())
    val sample = Output(UInt(6.W))
    val dmcActive = Output(Bool())
  })

  val irqEnable = RegInit(0.B)
  val irqActive = RegInit(0.B)
  val loop = RegInit(0.B)
  val freq = RegInit(0.U(4.W))
  val dac = RegInit(0.U(7.W))
  val sampleAddr = RegInit(0.U(8.W))
  val sampleLen = RegInit(0.U(8.W))
  val shiftReg = RegInit(0x07.U(8.W))
  val cycles = RegInit(439.U(9.W))
  val address = RegInit(0.U(15.W))
  val bytesRemaining = RegInit(0.U(12.W))
  val bitsUsedInSampleBuf = RegInit(0.U(3.W))
  val sampleBuf = RegInit(0.U(8.W))
  val sampleBufLoaded = RegInit(0.B)
  val shiftRegLoaded = RegInit(0.B)
  val dmcEnabled = RegInit(0.B)
  val activationDelay = RegInit(0.B)
  io.dmaAddr := Cat(1.B,address)
  io.sample := dac
  io.irq := irqActive
  io.dmcActive := dmcEnabled
  io.dmaReq := ~sampleBufLoaded & dmcEnabled & ~activationDelay(0)

  val newPeriod = VecInit(
    428.U(9.W),
    380.U(9.W),
    340.U(9.W),
    320.U(9.W),
    286.U(9.W),
    254.U(9.W),
    226.U(9.W),
    214.U(9.W),
    190.U(9.W),
    160.U(9.W),
    142.U(9.W),
    128.U(9.W),
    106.U(9.W),
    84.U(9.W),
    72.U(9.W),
    54.U(9.W)
  )

  when(io.CE) {
    when(activationDelay === 3.U & !io.odd_even) {
      activationDelay := 1.U
    }
    when(activationDelay === 1.U) {
      activationDelay := 0.U
    }

    when(io.WE) {
      when(io.addr === 0.U) {
        irqEnable := io.dataIn(7)
        loop := io.dataIn(6)
        freq := io.dataIn(3,0)
        when(!io.dataIn(7)) {
          irqActive := 0.B
        }
      }
      when(io.addr === 1.U) {
        dac := io.dataIn(6,0)
      }
      when(io.addr === 2.U) {
        sampleAddr := io.dataIn
      }
      when(io.addr === 3.U) {
        sampleLen := io.dataIn
      }
      when(io.addr === 5.U) {
        irqActive := 0.B
        dmcEnabled := io.dataIn(4)
        when(io.dataIn(4) & !dmcEnabled) {
          address := Cat(1.B,sampleAddr,0.U(6.W))
          bytesRemaining := Cat(sampleLen,0.U(4.W))
          activationDelay := 3.U
        }
      }
    }
    cycles := cycles - 1.U
    when(cycles === 1.U) {
      cycles := newPeriod(freq)
      when(shiftRegLoaded) {
        when(shiftReg(0)) {
          dac := Cat(Mux(!dac(6,1).andR, dac(6,1) + 1.U,dac(6,1)),dac(0))
        }
        .otherwise {
          dac := Cat(Mux(!dac(6,1).orR, dac(6,1) + "b111111".U,dac(6,1)),dac(0))
        }
      }
      shiftReg := Cat(0.B,shiftReg(7,1))
      bitsUsedInSampleBuf := bitsUsedInSampleBuf + 1.U
      when(bitsUsedInSampleBuf === 7.U) {
        shiftRegLoaded := sampleBufLoaded
        shiftReg := sampleBuf
        sampleBufLoaded := 0.B
      }
    }
    when(io.dmaAck) {
      address := address + 1.U
      bytesRemaining := bytesRemaining - 1.U
      sampleBufLoaded := 1.B
      sampleBuf := io.dmaData
      when(bytesRemaining === 0.U) {
        address := Cat(1.B, sampleAddr, 0.U(6.W))
        bytesRemaining := Cat(sampleLen, 0.U(4.W))
        dmcEnabled := loop
        when(!loop & irqEnable) {
          irqActive := 1.B
        }
      }
    }
  }
}
