package ee.catgirl.nes.cpu

import chiseltest._
import chiseltest.simulator.SimulatorAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import ee.catgirl.nes.util.{AsyncROMBlackBoxFactory, ROMTester, SyncROMBlackBoxFactory}
import org.scalatest.ParallelTestExecution
import treadle.BlackBoxFactoriesAnnotation

class NESBasicCPUTests extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  protected def DefaultBackend: SimulatorAnnotation = VerilatorBackendAnnotation

  private def DefaultAnnos = Seq(DefaultBackend,WriteFstAnnotation,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)))

  // No point generating VCDs for currently passing tests.
  private def NOVCDAnnos = Seq(DefaultBackend,BlackBoxFactoriesAnnotation(Seq(new AsyncROMBlackBoxFactory,new SyncROMBlackBoxFactory)))

  it should "be able to pass 01-basics.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/01-basics.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 1000000)
  }

  it should "be able to pass 02-implied.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/02-implied.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 03-immediate.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/03-immediate.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(10000000)
  }

  it should "be able to pass 04-zero_page.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/04-zero_page.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(10000000)
  }

  it should "be able to pass 05-zp_xy.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/05-zp_xy.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(100000000)
  }

  it should "be able to pass 06-absolute.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/06-absolute.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(10000000)
  }

  it should "be able to pass 07-abs_xy.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/07-abs_xy.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(100000000)
  }

  it should "be able to pass 08-ind_x.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/08-ind_x.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(10000000)
  }

  it should "be able to pass 09-ind_y.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/09-ind_y.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilAssertFail(10000000)
  }

  it should "be able to pass 10-branches.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/10-branches.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 11-stack.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/11-stack.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 100000000)
  }

  it should "be able to pass 12-jmp_jsr.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/12-jmp_jsr.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 13-rts.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/13-rts.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 14-rti.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/14-rti.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 15-brk.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/15-brk.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass 16-special.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/rom_singles/16-special.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 10000000)
  }

  it should "be able to pass official_only.nes" in {
    test(new ROMTester(romPath = getClass.getResource("/nes-test-roms/instr_test-v5/official_only.nes").getPath)).withAnnotations(NOVCDAnnos).runUntilStop(timeout = 1000000000)
  }
}
