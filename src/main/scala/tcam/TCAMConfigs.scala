package tcam

import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet}

// Base configuration for TCAM
class WithTCAM(params: TCAMParams = TCAMParams()) extends Config((site, here, up) => {
  case TCAMKey => Some(params)
})

// 64x28 TCAM configuration
class WithTCAM64x28 extends Config(
  new WithTCAM(TCAMParams(
    width = 32,
    keyWidth = 28,  // queryStrLen
    entries = 64,   // potMatchAddr
    sramDepth = 512,
    sramWidth = 32,
    priorityEncoder = true,
    address = 0x4000,
    useAXI4 = false,
    tableConfig = Some(TCAMTableConfig(
      queryStrLen = 28,
      subStrLen = 7,
      totalSubStr = 4,
      potMatchAddr = 64
    ))
  ))
)

// 64x28 TCAM configuration with AXI4
class WithTCAM64x28AXI4 extends Config(
  new WithTCAM(TCAMParams(
    width = 32,
    keyWidth = 28,  // queryStrLen
    entries = 64,   // potMatchAddr
    sramDepth = 512,
    sramWidth = 32,
    priorityEncoder = true,
    address = 0x4000,
    useAXI4 = true,
    tableConfig = Some(TCAMTableConfig(
      queryStrLen = 28,
      subStrLen = 7,
      totalSubStr = 4,
      potMatchAddr = 64
    ))
  ))
)

// 32x28 TCAM configuration
class WithTCAM32x28 extends Config(
  new WithTCAM(TCAMParams(
    width = 32,
    keyWidth = 28,  // 28-bit address
    entries = 32,   // 32 entries (6-bit output)
    sramDepth = 256,
    sramWidth = 32,
    priorityEncoder = true,
    address = 0x4000,
    useAXI4 = false,
    tableConfig = Some(TCAMTableConfig(
      queryStrLen = 28,
      subStrLen = 7,
      totalSubStr = 4,
      potMatchAddr = 32
    ))
  ))
)


// 32x28 TCAM configuration with AXI4
class WithTCAM32x28AXI4 extends Config(
  new WithTCAM(TCAMParams(
    width = 32,
    keyWidth = 28,  // 28-bit address
    entries = 32,   // 32 entries (6-bit output)
    sramDepth = 256,
    sramWidth = 32,
    priorityEncoder = true,
    address = 0x4000,
    useAXI4 = true,
    tableConfig = Some(TCAMTableConfig(
      queryStrLen = 28,
      subStrLen = 7,
      totalSubStr = 4,
      potMatchAddr = 32
    ))
  ))
)


class WithTCAMRoCC64x28 extends Config((site, here, up) => {
  case BuildRoCC => Seq((p: Parameters) => LazyModule(
    new TCAMRoCC(OpcodeSet.custom0, TCAMParams(
      width = 32,
      keyWidth = 28,
      entries = 64,
      sramDepth = 512,
      sramWidth = 32,
      priorityEncoder = true,
      address = 0x4000,
      useAXI4 = false,
      tableConfig = Some(TCAMTableConfig(
        queryStrLen = 28,
        subStrLen = 7,
        totalSubStr = 4,
        potMatchAddr = 64
      ))
    ))(p)))
})

class WithTCAMRoCC32x28 extends Config((site, here, up) => {
  case BuildRoCC => Seq((p: Parameters) => LazyModule(
    new TCAMRoCC(OpcodeSet.custom0, TCAMParams(
      width = 32,
      keyWidth = 28,
      entries = 32,
      sramDepth = 256,
      sramWidth = 32,
      priorityEncoder = true,
      address = 0x4000,
      useAXI4 = false,
      tableConfig = Some(TCAMTableConfig(
        queryStrLen = 28,
        subStrLen = 7,
        totalSubStr = 4,
        potMatchAddr = 32
      ))
    ))(p)))
})

