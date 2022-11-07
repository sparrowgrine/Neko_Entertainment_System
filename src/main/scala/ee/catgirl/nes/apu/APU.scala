package ee.catgirl.nes.apu

import chisel3._
import chisel3.util._

class APU extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(5.W))
    val dataIn = Input(UInt(8.W))
    val dataOut = Output(UInt(8.W))
    val CE = Input(Bool())
    val WE = Input(Bool())
    val RD = Input(Bool())
    val sample = Output(UInt(16.W))

    val dmaReq = Output(Bool())
    val dmaAck = Input(Bool())
    val dmaAddr = Output(UInt(16.W))
    val dmaData = Input(UInt(8.W))
    val oddEven = Output(Bool())
    val irq = Output(Bool())
  })

  val channelEnable = RegInit(VecInit.fill(4){0.B})
  val frameSequencerMode = RegInit(0.B)
  val cycles = RegInit(0.U(16.W))
  val envClock = RegInit(0.B)
  val lenClock = RegInit(0.B)
  val writeTo4017 = RegInit(0.B)
  val irqCounter = RegInit(0.U(2.W))
  val intClock = RegInit(0.B)
  io.oddEven := intClock

  val pulseChannel1 = Module(new PulseChannel(false))
  pulseChannel1.io.CE := io.CE
  pulseChannel1.io.addr := io.addr(1,0)
  pulseChannel1.io.dataIn := io.dataIn
  pulseChannel1.io.WE := io.WE & (io.addr(4,2) === 0.U)
  pulseChannel1.io.envelopeClock := envClock
  pulseChannel1.io.lengthCounterClock := lenClock
  pulseChannel1.io.en := channelEnable(0)

  val pulseChannel2 = Module(new PulseChannel(true))
  pulseChannel2.io.CE := io.CE
  pulseChannel2.io.addr := io.addr(1, 0)
  pulseChannel2.io.dataIn := io.dataIn
  pulseChannel2.io.WE := io.WE & (io.addr(4, 2) === 1.U)
  pulseChannel2.io.envelopeClock := envClock
  pulseChannel2.io.lengthCounterClock := lenClock
  pulseChannel2.io.en := channelEnable(1)

  val triangleChannel = Module(new TriangleChannel)
  triangleChannel.io.CE := io.CE
  triangleChannel.io.addr := io.addr(1, 0)
  triangleChannel.io.dataIn := io.dataIn
  triangleChannel.io.WE := io.WE & (io.addr(4, 2) === 2.U)
  triangleChannel.io.linearCounterClock := envClock
  triangleChannel.io.lengthCounterClock := lenClock
  triangleChannel.io.en := channelEnable(2)

  val noiseChannel = Module(new NoiseChannel)
  noiseChannel.io.CE := io.CE
  noiseChannel.io.addr := io.addr(1, 0)
  noiseChannel.io.dataIn := io.dataIn
  noiseChannel.io.WE := io.WE & (io.addr(4, 2) === 3.U)
  noiseChannel.io.envelopeClock := envClock
  noiseChannel.io.lengthCounterClock := lenClock
  noiseChannel.io.en := channelEnable(3)

  val dmcChannel = Module(new DMCChannel)
  dmcChannel.io.CE := io.CE
  dmcChannel.io.addr := io.addr(2, 0)
  dmcChannel.io.dataIn := io.dataIn
  dmcChannel.io.WE := io.WE & (io.addr(4, 2) >= 4.U)
  dmcChannel.io.odd_even := io.oddEven
  io.dmaReq := dmcChannel.io.dmaReq
  dmcChannel.io.dmaAck := io.dmaAck
  dmcChannel.io.dmaData := io.dmaData
  io.dmaAddr := dmcChannel.io.dmaAddr

  val frameInterrupt = RegInit(0.B)
  val frameInterruptDisable = RegInit(0.B)
  val frameInterruptIOTrigger = (io.addr === 0x15.U && io.RD) || (io.WE && io.addr === Cat(5.U,3.U) && io.dataIn(6))

  when(io.CE) {
    frameInterrupt := Mux(irqCounter(1),1.B,Mux(frameInterruptIOTrigger,0.B,frameInterrupt))
    intClock := ~intClock
    irqCounter := Cat(irqCounter(0),0.B)
    lenClock := 0.B
    envClock := 0.B
    cycles := cycles + 1.U
    when(cycles === 7457.U) {
      envClock := 1.B
    }
    .elsewhen(cycles === 14913.U) {
      envClock := 1.B
      lenClock := 1.B
    }
    .elsewhen(cycles === 22371.U) {
      envClock := 1.B
    }
    .elsewhen(cycles === 29829.U) {
      when(!frameSequencerMode) {
        envClock := 1.B
        lenClock := 1.B
        cycles := 0.U
        irqCounter := 3.U
        frameInterrupt := 1.B
      }
    }
    .elsewhen(cycles === 37281.U) {
      envClock := 1.B
      lenClock := 1.B
      cycles := 0.U
    }

    writeTo4017 := 0.B
    when(writeTo4017) {
      when(frameSequencerMode) {
        envClock := 1.B
        lenClock := 1.B
      }
      cycles := 0.U
    }

    when(io.WE && io.addr(4,2) === 5.U) {

      when(io.addr(1,0) === 1.U) {
        channelEnable := io.dataIn(3,0).asBools
      }
      when(io.addr(1,0) === 3.U) {
        frameSequencerMode := io.dataIn(7)
        frameInterruptDisable := io.dataIn(6)

        when(!intClock) {
          when(io.dataIn(7)) {
            envClock := 1.B
            lenClock := 1.B
          }
          cycles := 0.U
        }
        writeTo4017 := intClock
      }
    }
  }

  val frameIrq = frameInterrupt & !frameInterruptDisable

  io.dataOut := Cat(dmcChannel.io.irq,
    frameIrq,
    0.B,
    dmcChannel.io.dmcActive,
    noiseChannel.io.nonZero,
    triangleChannel.io.nonZero,
    pulseChannel2.io.nonZero,
    pulseChannel1.io.nonZero)

  io.irq := frameIrq | dmcChannel.io.irq

  io.sample := APULUT(
    Cat(0.U(4.W),pulseChannel1.io.out) +
    Cat(0.U(4.W),pulseChannel2.io.out),
    Cat(0.U(4.W),triangleChannel.io.out) + Cat(0.U(3.W),triangleChannel.io.out,0.B) +
    Cat(0.U(3.W),noiseChannel.io.out,0.B))
}
