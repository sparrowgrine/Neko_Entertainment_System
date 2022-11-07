package ee.catgirl.nes.apu

import chiseltest._
import chiseltest.simulator.{SimulatorAnnotation, VerilatorFlags}
import org.scalatest.flatspec.AnyFlatSpec
import ee.catgirl.nes.util.{AsyncROMBlackBoxFactory, ROMTester, SyncROMBlackBoxFactory}
import org.scalatest.ParallelTestExecution
import treadle.BlackBoxFactoriesAnnotation

class NESBasicAPUTests extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  protected def DefaultBackend: SimulatorAnnotation = VerilatorBackendAnnotation

  private def DefaultAnnos = Seq(DefaultBackend,WriteFstAnnotation,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)),VerilatorFlags(Seq("--timescale","100ns/100ns")))

  // No point generating VCDs for currently passing tests.
  private def NOVCDAnnos = Seq(DefaultBackend,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)),VerilatorFlags(Seq("--timescale","100ns/100ns")))



  it should "be able to pass 1-len_ctr.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/1-len_ctr.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 2-len_table.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/2-len_table.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 3-irq_flag.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/3-irq_flag.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 4-jitter.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/4-jitter.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 5-len_timing.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/5-len_timing.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 6-irq_flag_timing.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/6-irq_flag_timing.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 7-dmc_basics.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/7-dmc_basics.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 100000000)
  }

  it should "be able to pass 8-dmc_rates.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_test/rom_singles/8-dmc_rates.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 1000000000)
  }

  it should "be able to pass 4015_cleared.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_reset/4015_cleared.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 4017_timing.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_reset/4017_timing.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 4017_written.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_reset/4017_written.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(timeout = 10000000)//.runUntilStop(timeout = 10000000)
  }

  it should "be able to pass irq_flag_cleared.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_reset/irq_flag_cleared.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass len_ctrs_enabled.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_reset/len_ctrs_enabled.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(timeout = 10000000)//.runUntilStop(timeout = 10000000)
  }

  it should "be able to pass works_immediately.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/apu_reset/works_immediately.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }
}
