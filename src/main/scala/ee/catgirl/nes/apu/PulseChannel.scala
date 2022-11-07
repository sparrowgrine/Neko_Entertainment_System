package ee.catgirl.nes.apu

import chisel3._
import chisel3.util._
import chisel3.internal.firrtl.Index

class PulseChannel(sq2 : Boolean) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(2.W))
    val dataIn = Input(UInt(8.W))
    val WE = Input(Bool())
    val CE = Input(Bool())
    val en = Input(Bool())
    val lengthCounterClock = Input(Bool())
    val envelopeClock = Input(Bool())
    val out = Output(UInt(4.W))
    val nonZero = Output(Bool())
  })

  class ControlRegDef extends Bundle {
    val duty = UInt(2.W)
    val envLoop = Bool()
    val envDisable = Bool()
    val envPeriod = UInt(4.W)
  }

  class SweepRegDef extends Bundle {
    val enable = Bool()
    val period = UInt(3.W)
    val negate = Bool()
    val shiftCount = UInt(3.W)
  }

  class TimerCtrlRegDef extends Bundle {
    val lengthIndex = UInt(5.W)
    val periodHigh = UInt(3.W)
  }

  val ctrlReg = RegInit(0.U(8.W).asTypeOf(new ControlRegDef))
  val sweepReg = RegInit(0.U(8.W).asTypeOf(new SweepRegDef))
  val periodLow = RegInit(0.U(8.W))
  val timerCtrl = RegInit(0.U(8.W).asTypeOf(new TimerCtrlRegDef))
  
  val lengthCounter = RegInit(0.U(8.W))
  val resetEnv = RegInit(0.B)
  io.nonZero := lengthCounter =/= 0.U
  val lenCounterHalt = WireDefault(ctrlReg.envLoop)

  val envelope = RegInit(0.U(4.W))
  val envelopeDivider = RegInit(0.U(4.W))
  val sweepReset = RegInit(0.B)
  val sweepDivider = RegInit(0.U(3.W))

  val period = WireDefault(Cat(timerCtrl.periodHigh,periodLow))

  val timerCounter = RegInit(0.U(12.W))
  val seqPos = RegInit(0.U(3.W))
  val shiftedPeriod = WireDefault(period >> sweepReg.shiftCount)
  val periodRhs = WireDefault(Mux(sweepReg.negate,~shiftedPeriod +& Cat(0.U(10.W),sq2.B),shiftedPeriod))
  val newSweepPeriod = WireDefault(period +& periodRhs)
  val validFreq = WireDefault((period(10,3) >= 8.U) && (sweepReg.negate || !newSweepPeriod(11)))

  when(io.CE) {
    when(io.WE ) {
      when(io.addr === 0.U) {
          ctrlReg := io.dataIn.asTypeOf(ctrlReg)
        }
        when(io.addr === 1.U) {
          sweepReg := io.dataIn.asTypeOf(sweepReg)
          sweepReset := 1.B
        }
        when(io.addr === 2.U) {
          periodLow := io.dataIn
        }
        when(io.addr === 3.U) {
          timerCtrl := io.dataIn.asTypeOf(timerCtrl)
          lengthCounter := LengthCounterLUT(io.dataIn(7,3))
          resetEnv := 1.B
          seqPos := 0.U
        }
    }

    when(timerCounter === 0.U) {
      timerCounter := Cat(period,0.U)
      seqPos := seqPos - 1.U
    }
    .otherwise {
      timerCounter := timerCounter - 1.U
    }

    when(io.lengthCounterClock && lengthCounter =/= 0.U && !lenCounterHalt) {
      lengthCounter := lengthCounter - 1.U
    }

    when(io.lengthCounterClock) {
      when(sweepDivider === 0.U) {
        sweepDivider := sweepReg.period
        when(sweepReg.enable && sweepReg.shiftCount =/= 0.U && validFreq) {
          periodLow := newSweepPeriod(7,0)
          timerCtrl.periodHigh := newSweepPeriod(10,8)
        }
      }
      .otherwise {
        sweepDivider := sweepDivider - 1.U
      }
      when(sweepReset) {
        sweepDivider := sweepReg.period
      }
      sweepReset := 0.B
    }

    when(io.envelopeClock) {
      when(resetEnv) {
        envelopeDivider := ctrlReg.envPeriod
        envelope := 15.U
        resetEnv := 0.B
      }
      .elsewhen(envelopeDivider === 0.U) {
        envelopeDivider := ctrlReg.envPeriod
        when(envelope =/= 0.U || ctrlReg.envLoop) {
          envelope := envelope - 1.U
        }
      }
      .otherwise {
        envelopeDivider := envelopeDivider - 1.U
      }

    }

    when(!io.en) {
      lengthCounter := 0.U
    }
  }

  val dutyEnabled = WireDefault(VecInit(seqPos === 7.U, seqPos >= 6.U, seqPos >= 4.U, seqPos < 6.U)(ctrlReg.duty))

  when(lengthCounter === 0.U || !validFreq || !dutyEnabled) {
    io.out := 0.U
  }
  .otherwise {
      io.out := Mux(ctrlReg.envDisable,ctrlReg.envPeriod,envelope)
  }
}
