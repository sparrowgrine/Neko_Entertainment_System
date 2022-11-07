package ee.catgirl.nes.apu

import chisel3._
import chisel3.util._

class TriangleChannel extends Module {
    val io = IO(new Bundle {
        val addr = Input(UInt(2.W))
        val dataIn = Input(UInt(8.W))
        val CE = Input(Bool())
        val WE = Input(Bool())
        val lengthCounterClock = Input(Bool())
        val linearCounterClock = Input(Bool())
        val en = Input(Bool())
        val out = Output(UInt(4.W))
        val nonZero = Output(Bool())
    })

    val periodLow = RegInit(0.U(7.W))
    val periodHigh = RegInit(0.U(4.W))
    val period = Cat(periodHigh,periodLow)
    val timerCounter = RegInit(0.U(11.W))
    val seqPos = RegInit(0.U(5.W))
    val linearCounterPeriod = RegInit(0.U(7.W))
    val linearCounter = RegInit(0.U(7.W))
    val linearControl = RegInit(0.B)
    val linearHalt = RegInit(0.B)
    val linearCounterZero = ~linearCounter.orR
    val lengthCounter = RegInit(0.U(8.W))
    val lengthCounterHalt = linearControl
    val lengthCounterZero = !lengthCounter.orR

    io.nonZero := lengthCounter =/= 0.U

    when(io.CE) {
        when(io.WE) {
            when(io.addr === 0.U) {
                linearControl := io.dataIn(7)
                linearCounterPeriod := io.dataIn(6,0)
            }
            when(io.addr === 2.U) {
                periodLow := io.dataIn
            }
            when(io.addr === 3.U) {
                periodHigh := io.dataIn(2,0)
                lengthCounter := LengthCounterLUT(io.dataIn(7,3))
                linearHalt := 1.B
            }
        }

        timerCounter := Mux(timerCounter === 0.U, period, timerCounter - 1.U)

        when(io.lengthCounterClock & !lengthCounterZero & !lengthCounterHalt) {
            lengthCounter := lengthCounter - 1.U
        }

        when(io.linearCounterClock) {
            when(linearHalt) {
                linearCounter := linearCounterPeriod
            }
            .elsewhen(~linearCounterZero) {
                linearCounter := linearCounter - 1.U
            }
            .otherwise {
                linearHalt := 0.B
            }
        }

        when(~io.en) {
            lengthCounter := 0.U
        }

        when(timerCounter === 0.U & ~lengthCounterZero & ~linearCounterZero) {
            seqPos := seqPos + 1.U
        }
    }

    io.out := seqPos(3,0) ^ Fill(4,~seqPos(4))
}
