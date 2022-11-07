package ee.catgirl.nes

import chisel3._
import ee.catgirl.nes.apu.APU

class NESTop extends Module {
  val io = IO(new Bundle {
    val cpuBusAddr = Output(UInt(16.W))
    val cpuBusDataIn = Input(UInt(8.W))
    val cpuBusDataOut = Output(UInt(8.W))
    val cpuBusWE = Output(Bool())
    val cpuExtAccessRdy = Input(Bool())
    val extIrq = Input(Bool())
    val apuSample = Output(UInt(16.W))
  })

  val cycleCounter = RegInit(0.U(2.W))
  cycleCounter := Mux(cycleCounter === 2.U, 0.U, cycleCounter + 1.U)

  val cpuComplexEnable = (cycleCounter === 2.U)

  val apu = Module(new APU())
  val dmac = Module(new DMAController())
  val cpu = Module(new CPU())

  val mapperIrq = RegNext(io.extIrq,0.B)
  val apuIrq = RegInit(0.B)
  when(cpuComplexEnable) {
    apuIrq := apu.io.irq
  }

  val dataToCPU = Reg(UInt(8.W))
  val busAddr = WireDefault(Mux(dmac.io.busRequest, dmac.io.addressOut, cpu.io.AB))
  val busData = WireDefault(Mux(dmac.io.busRequest, dmac.io.dataToMem, cpu.io.DO))
  val readStrobe = WireDefault(Mux(dmac.io.busRequest, dmac.io.isRead, !cpu.io.WE))
  val writeStrobe = WireDefault(Mux(dmac.io.busRequest, !dmac.io.isRead, cpu.io.WE))
  io.cpuBusWE := writeStrobe
  io.cpuBusDataOut := busData
  io.cpuBusAddr := busAddr

  dmac.io.CE := cpuComplexEnable
  dmac.io.oddCycle := apu.io.oddEven
  dmac.io.cpuInReadCycle := ~cpu.io.WE
  dmac.io.dataFromCPU := cpu.io.DO
  dmac.io.spriteInitiated := (busAddr === 0x4014.U & writeStrobe)
  dmac.io.dmcInitiated := apu.io.dmaReq
  dmac.io.dataFromMem := io.cpuBusDataIn
  dmac.io.dmcDmaAddr := apu.io.dmaAddr
  apu.io.dmaData := io.cpuBusDataIn
  apu.io.dmaAck := dmac.io.dmcAck

  cpu.io.CE := cpuComplexEnable & ~dmac.io.cpuHalt
  cpu.io.DI := dataToCPU
  cpu.io.NMI := false.B
  cpu.io.IRQ := mapperIrq | apuIrq
  cpu.io.RDY := io.cpuExtAccessRdy & cpuComplexEnable & ~dmac.io.cpuHalt

  val apuCS = WireDefault(busAddr >= 0x4000.U && busAddr < 0x4018.U)

  apu.io.CE := cpuComplexEnable
  apu.io.addr := busAddr(4,0)
  apu.io.dataIn := busData
  apu.io.WE := writeStrobe & apuCS
  apu.io.RD := readStrobe & apuCS
  io.apuSample := apu.io.sample

  when(apuCS) {
    dataToCPU := apu.io.dataOut
  }
  .otherwise {
    dataToCPU := io.cpuBusDataIn
  }
}
