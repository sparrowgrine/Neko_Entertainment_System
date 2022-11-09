package ee.catgirl.nes.ppu


import chiseltest._
import chiseltest.simulator.SimulatorAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import ee.catgirl.nes.util.{AsyncROMBlackBoxFactory, ROMTester, SyncROMBlackBoxFactory}
import org.scalatest.ParallelTestExecution
import treadle.BlackBoxFactoriesAnnotation

class NESBasicPPUTests extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  protected def DefaultBackend: SimulatorAnnotation = VerilatorBackendAnnotation

  private def DefaultAnnos = Seq(DefaultBackend,WriteFstAnnotation,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)))

  // No point generating VCDs for currently passing tests.
  private def NOVCDAnnos = Seq(DefaultBackend,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)))

  it should "be able to pass 01-vbl_basics.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/ppu_vbl_nmi/rom_singles/01-vbl_basics.nes").getPath)).withAnnotations(DefaultAnnos).runUntilStop(timeout = 100000000)
  }
}
