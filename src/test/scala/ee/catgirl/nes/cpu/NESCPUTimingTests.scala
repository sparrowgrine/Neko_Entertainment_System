package ee.catgirl.nes.cpu

import chiseltest._
import chiseltest.simulator.SimulatorAnnotation
import ee.catgirl.nes.util.{AsyncROMBlackBoxFactory, ROMTester, SyncROMBlackBoxFactory}
import org.scalatest.ParallelTestExecution
import org.scalatest.flatspec.AnyFlatSpec
import treadle.BlackBoxFactoriesAnnotation

class NESCPUTimingTests extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  protected def DefaultBackend: SimulatorAnnotation = VerilatorBackendAnnotation

  private def DefaultAnnos = Seq(DefaultBackend,WriteFstAnnotation,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)))

  // No point generating VCDs for currently passing tests.
  private def NOVCDAnnos = Seq(DefaultBackend,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)))

  it should "be able to pass instr_timing.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_timing/instr_timing.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(timeout = 100000000) //runUntilStop(timeout = 100000000)
  }
}
