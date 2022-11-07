package ee.catgirl.nes.apu

import chiseltest._
import chiseltest.simulator.{SimulatorAnnotation, VerilatorFlags}
import org.scalatest.flatspec.AnyFlatSpec
import ee.catgirl.nes.util.{AsyncROMBlackBoxFactory, ROMTester, SyncROMBlackBoxFactory}
import org.scalatest.ParallelTestExecution
import treadle.BlackBoxFactoriesAnnotation

class NESAPURecordingTests extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  protected def DefaultBackend: SimulatorAnnotation = VerilatorBackendAnnotation

  private def DefaultAnnos = Seq(DefaultBackend,WriteFstAnnotation,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)),VerilatorFlags(Seq("--timescale","100ns/100ns")))

  // No point generating VCDs for currently passing tests.
  private def NOVCDAnnos = Seq(DefaultBackend,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)),VerilatorFlags(Seq("--timescale","100ns/100ns")))

  // TODO: Implement VBLANK, otherwise these tests stall out.

  // it should "be able to pass square.nes" in {
  //   test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_mixer/square.nes").getPath)).withAnnotations(DefaultAnnos).runUntilStop(timeout = 10000000)
  // }

  // it should "be able to pass triangle.nes" in {
  //   test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_mixer/triangle.nes").getPath)).withAnnotations(DefaultAnnos).runUntilStop(timeout = 10000000)
  // }

  // it should "be able to pass noise.nes" in {
  //   test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_mixer/noise.nes").getPath)).withAnnotations(DefaultAnnos).runUntilStop(timeout = 10000000)
  // }

  // it should "be able to pass dmc.nes" in {
  //   test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_mixer/dmc.nes").getPath)).withAnnotations(DefaultAnnos).runUntilStop(timeout = 10000000)
  // }
}
