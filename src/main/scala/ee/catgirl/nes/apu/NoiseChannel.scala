package ee.catgirl.nes.apu

import chisel3._
import chisel3.util._

class NoiseChannel extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(2.W))
    val dataIn = Input(UInt(8.W))
    val CE = Input(Bool())
    val WE = Input(Bool())
    val lengthCounterClock = Input(Bool())
    val envelopeClock = Input(Bool())
    val en = Input(Bool())
    val out = Output(UInt(4.W))
    val nonZero = Output(Bool())
  })

  val envelopeLoop = RegInit(0.B)
  val envelopeDisable = RegInit(0.B)
  val resetEnvelope = RegInit(0.B)
  val volume = RegInit(0.U(4.W))
  val envelope = RegInit(0.U(4.W))
  val envelopeDivider = RegInit(0.U(4.W))
  val lengthCounterHalt = envelopeLoop
  val lengthCounter = RegInit(0.U(8.W))
  val shortMode = RegInit(0.B)
  val lfsr = RegInit(1.U(15.W))
  val lengthCounterZero = !lengthCounter.orR

  io.nonZero := lengthCounter =/= 0.U

  val period = RegInit(0.U(4.W))
  val noisePeriod = Wire(UInt(12.W))
  val timerCounter = RegInit(0.U(12.W))

  noisePeriod := MuxLookup(period,noisePeriod,IndexedSeq(
    0x0.U -> 0x004.U,
    0x1.U -> 0x008.U,
    0x2.U -> 0x010.U,
    0x3.U -> 0x020.U,
    0x4.U -> 0x040.U,
    0x5.U -> 0x060.U,
    0x6.U -> 0x080.U,
    0x7.U -> 0x0A0.U,
    0x8.U -> 0x0CA.U,
    0x9.U -> 0x0FE.U,
    0xA.U -> 0x17C.U,
    0xB.U -> 0x1FC.U,
    0xC.U -> 0x2FA.U,
    0xD.U -> 0x3F8.U,
    0xE.U -> 0x7F2.U,
    0xF.U -> 0xFE4.U
  ))

  when(io.CE) {
    when(io.WE) {
      when(io.addr === 0.U) {
        envelopeLoop := io.dataIn(5)
        envelopeDisable := io.dataIn(5)
        volume := io.dataIn(3,0)
      }
      when(io.addr === 2.U) {
        shortMode := io.dataIn(7)
        period := io.dataIn(3,0)
      }
      when(io.addr === 3.U) {
        lengthCounter := LengthCounterLUT(io.dataIn(7,3))
        resetEnvelope := 1.B
      }
    }

    when(io.lengthCounterClock & !lengthCounterZero & !lengthCounterHalt) {
      lengthCounter := lengthCounter - 1.U
    }


    when(timerCounter === 0.U) {
      timerCounter := noisePeriod
      lfsr := Cat(lfsr(0) ^ Mux(shortMode,lfsr(6),lfsr(1)),lfsr(14,1))
    }
    .otherwise {
      timerCounter := timerCounter - 1.U
    }

    when(io.envelopeClock) {
      when(resetEnvelope) {
        envelopeDivider := volume
        envelope := 15.U
        resetEnvelope := 0.B
      }
      .elsewhen(envelopeDivider === 0.U) {
        envelopeDivider := volume
        when(envelope =/= 0.U) {
          envelope := envelope - 1.U
        }
        .elsewhen(envelopeLoop) {
          envelope := 15.U
        }
      }
      .otherwise {
        envelopeDivider := envelopeDivider - 1.U
      }
    }

    when(~io.en) {
      lengthCounter := 0.U
    }
  }
  io.out := Mux(lengthCounter === 0.U || lfsr(0),0.U,Mux(envelopeDisable,volume,envelope))
}
