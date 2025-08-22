# TCAM-generator

A TCAM (Ternary Content Addressable Memory) generator for Chipyard-based designs.

## Prerequisites

**Important**: This repository is not standalone and requires [pristine-chipyard](https://github.com/merledu/pristine-chipyard.git) to run. Please ensure you have Chipyard properly set up in your environment before attempting to use this TCAM generator.

## Overview

This project provides tools and configurations for generating TCAM components that can be integrated into Chipyard-based chip designs. The TCAM generator supports both ROCC (Rocket Custom Coprocessor) and MMIO (Memory-Mapped I/O) interfaces, making it flexible for different integration scenarios.

## Architecture

### ROCC Implementation

The TCAM can be integrated as a Rocket Custom Coprocessor (ROCC) using the `TCAMRoCC` class. This provides:

- **Custom Instructions**: Uses `OpcodeSet.custom0` for TCAM operations
- **State Machine**: Implements a 4-state FSM (Idle → Execute → Probe → Response)
- **Operations**:
  - **Write** (`funct=0`): Write data to TCAM with write mask
  - **Read** (`funct=1`): Read from TCAM (inactive operation)
  - **Search** (`funct=2`): Perform TCAM search operation
  - **Status** (`funct=3`): Get match status and priority match address (PMA)

- **Interface**: 
  - `rs1[31:28]`: Write mask (4-bit)
  - `rs1[27:0]`: Address (28-bit)
  - `rs2[31:0]`: Write data (32-bit)
  - `rd`: Destination register for results

### MMIO Implementation

The TCAM supports two MMIO interface types:

#### TileLink MMIO (`TCAMTL`)
- **Base Address**: Configurable (default: 0x4000)
- **Register Map**:
  - `0x00`: Status register (read-only, contains PMA)
  - `0x04`: Control register (8-bit, controls chip select and write enable)
  - `0x08`: Write data register (32-bit)
  - `0x0C`: Address register (28-bit)

#### AXI4 MMIO (`TCAMAXI4`)
- **Base Address**: Configurable (default: 0x4000)
- **Register Map**: Same as TileLink implementation
- **Interface**: AXI4-compliant for systems requiring AXI4 protocol

## Configuration Options

### TCAM Parameters

The `TCAMParams` case class provides the following configuration options:

```scala
case class TCAMParams(
  width: Int = 32,                    // Data width
  keyWidth: Int = 28,                 // Search key width  
  entries: Int = 64,                  // Number of TCAM entries
  sramDepth: Int = 256,               // SRAM depth
  sramWidth: Int = 32,                // SRAM width
  priorityEncoder: Boolean = true,     // Enable priority encoding
  address: BigInt = 0x4000,           // MMIO base address
  useAXI4: Boolean = false,           // Use AXI4 instead of TileLink
  tableConfig: Option[TCAMTableConfig] = None  // Specific table configuration
)
```

### Table Configuration

```scala
case class TCAMTableConfig(
  queryStrLen: Int,    // Total query string length
  subStrLen: Int,      // Length of each substring
  totalSubStr: Int,    // Number of substrings
  potMatchAddr: Int    // Number of potential match addresses
)
```

### Pre-configured Configurations

#### 64x28 TCAM Configurations
- **`WithTCAM64x28TL`**: 64 entries × 28-bit keys with TileLink interface
- **`WithTCAM64x28AXI4`**: 64 entries × 28-bit keys with AXI4 interface
- **`WithTCAMRoCC64x28`**: 64 entries × 28-bit keys with ROCC interface

#### 32x28 TCAM Configurations
- **`WithTCAM32x28TL`**: 32 entries × 28-bit keys with TileLink interface
- **`WithTCAM32x28AXI4`**: 32 entries × 28-bit keys with AXI4 interface
- **`WithTCAMRoCC32x28`**: 32 entries × 28-bit keys with ROCC interface

### Configuration Details

| Configuration | Entries | Key Width | SRAM Depth | Output Width | Interface |
|---------------|---------|------------|------------|--------------|-----------|
| 32x28 | 32 | 28-bit | 256 | 6-bit | TileLink/AXI4/ROCC |
| 64x28 | 64 | 28-bit | 512 | 6-bit | TileLink/AXI4/ROCC |

## Hardware Implementation

### TCAM Core
- **Memory**: Uses OpenRAM SRAM models (`sky130_sram_1kbyte_1rw1r_32x256_8`)
- **Priority Encoder**: 64×6 priority encoder for match resolution
- **Block Structure**: 
  - 32x28: 2 memory blocks with 7-bit substrings
  - 64x28: 4 memory blocks with 7-bit substrings

### Interface Signals
- **`in_clk`**: Clock input
- **`in_csb`**: Chip select (active low)
- **`in_web`**: Write enable (active low)
- **`in_wmask`**: Write mask (4-bit)
- **`in_addr`**: Address (28-bit)
- **`in_wdata`**: Write data (32-bit)
- **`out_pma`**: Priority match address (5-6 bit)

### How `write_tcam` Function Works

The `write_tcam` function is used to write data to the TCAM by passing the data and address as inputs to the TCAM interface signals:

```c
void write_tcam(uint32_t data, uint32_t address) {
    reg_write32(TCAM_CONTROL, 0xF3); // csb=0, web=0, wmask=0xF
    reg_write32(TCAM_ADDRESS, address);
    reg_write32(TCAM_WDATA, data);
    delay_write(); 
}
```

**Function Parameters:**
- **`data`**: The 32-bit data to be written (maps to `in_wdata` signal)
- **`address`**: The 28-bit address where data will be written (maps to `in_addr` signal)

**Control Register (`TCAM_CONTROL`) Breakdown:**
The control register at address `TCAM_CONTROL` (0x4004) is used to configure the TCAM operation:

```
Bit 7-4: Write mask (wmask) - 0xF = 1111 (write all 4 bytes)
Bit 3-2: Reserved
Bit 1:   Write enable (web) - 0 = enable write (active low)
Bit 0:   Chip select (csb) - 0 = enable chip (active low)
```

**Signal Mapping:**
- **`in_wdata`** ← `data` parameter (32-bit write data)
- **`in_addr`** ← `address` parameter (28-bit address)
- **`in_wmask`** ← Control register bits 7-4 (0xF = write all bytes)
- **`in_web`** ← Control register bit 1 (0 = write enabled)
- **`in_csb`** ← Control register bit 0 (0 = chip enabled)

### How `search_tcam` Function Works

The `search_tcam` function is used to perform search operations in the TCAM:

```c
void search_tcam(uint32_t search_query) {
    reg_write32(TCAM_CONTROL, 0x01); // csb=0, web=1, wmask=0
    reg_write32(TCAM_ADDRESS, search_query);
    delay_read(); 
}
```

**Function Parameters:**
- **`search_query`**: The 28-bit search key to look for in TCAM (maps to `in_addr` signal)

**Control Register (`TCAM_CONTROL`) Breakdown:**
```
Bit 7-4: Write mask (wmask) - 0x0 = 0000 (no write)
Bit 3-2: Reserved
Bit 1:   Write enable (web) - 1 = disable write (active low)
Bit 0:   Chip select (csb) - 0 = enable chip (active low)
```

**Signal Mapping:**
- **`in_addr`** ← `search_query` parameter (28-bit search key)
- **`in_wdata`** ← Not used during search (set to 0)
- **`in_wmask`** ← Control register bits 7-4 (0x0 = no write)
- **`in_web`** ← Control register bit 1 (1 = read/search mode)
- **`in_csb`** ← Control register bit 0 (0 = chip enabled)

### How `read_tcam_status` Function Works

The `read_tcam_status` function reads the TCAM status register to get the priority match address:

```c
uint32_t read_tcam_status() {
    return reg_read32(TCAM_STATUS);
}
```

**Function Operation:**
- Reads from the status register at address `TCAM_STATUS` (0x4000)
- Returns the priority match address (PMA) from the last search operation
- No control register configuration needed - pure read operation

**Return Value:**
- **5-bit value** for 32x28 TCAM (32 entries)
- **6-bit value** for 64x28 TCAM (64 entries)
- **0** indicates no match found
- **Non-zero value** indicates the address of the highest priority match

### How `write_tcam_array` Function Works

The `write_tcam_array` function is a macro that writes an entire array to TCAM sequentially:

```c
#define write_tcam_array(data_array) do { \
    int size = sizeof(data_array) / sizeof(data_array[0]); \
    for(int i = 0; i < size; i++) { \
        write_tcam(data_array[i], i); \
    } \
} while(0)
```

**Function Operation:**
- Automatically calculates the size of the input array
- Iterates through each element starting from index 0
- Calls `write_tcam(data_array[i], i)` for each element
- Writes data to sequential addresses (0, 1, 2, 3, ...)

**Usage Example:**
```c
uint32_t test_data[] = {0x12345678, 0x87654321, 0xDEADBEEF};
write_tcam_array(test_data);
// This writes:
// - test_data[0] (0x12345678) to address 0
// - test_data[1] (0x87654321) to address 1  
// - test_data[2] (0xDEADBEEF) to address 2
```

**Advantages:**
- Convenient for bulk initialization
- Automatically handles address sequencing
- Reduces repetitive code
- Useful for loading predefined test patterns

## Test Programs and Implementation Details

The TCAM generator includes comprehensive test programs that demonstrate different usage patterns and configurations. These tests are located in the `tests/` directory and serve as reference implementations.

### Test Program Overview

#### 1. **tcam_32x28.c** - 32x28 TCAM MMIO Test
- **Purpose**: Tests 32-entry TCAM with 28-bit keys using MMIO interface
- **Key Features**:
  - Demonstrates bulk array writing using `write_tcam_array()`
  - Shows pattern matching with predefined test data
  - Uses 513-element test array with specific patterns
- **Test Data**: Includes patterns like `0x0000007f`, `0x00003f80`, `0x001fc000`, etc.
- **Search Query**: `0x0FFC3201`

#### 2. **tcam_64x28.c** - 64x28 TCAM MMIO Test
- **Purpose**: Tests 64-entry TCAM with 28-bit keys using MMIO interface
- **Key Features**:
  - Individual entry writing with `write_tcam()`
  - Sequential address pattern testing
  - Demonstrates different write patterns
- **Test Pattern**: Sequential writes with addresses `0x00000000`, `0x00000010`, etc.
- **Search Query**: `0x00A14285`

#### 3. **tcam_32x28rocc.c** - 32x28 TCAM ROCC Test
- **Purpose**: Tests 32-entry TCAM with 28-bit keys using ROCC interface
- **Key Features**:
  - Same test data as MMIO version but uses ROCC instructions
  - Demonstrates ROCC custom instruction usage
  - Shows compatibility between MMIO and ROCC interfaces
- **Test Data**: Identical to `tcam_32x28.c` for comparison

#### 4. **tcam_64x28rocc.c** - 64x28 TCAM ROCC Test
- **Purpose**: Tests 64-entry TCAM with 28-bit keys using ROCC interface
- **Key Features**:
  - Individual entry writing using ROCC custom instructions
  - Demonstrates ROCC interface efficiency
  - Shows pattern matching capabilities

### Test Program API and Usage Examples

The test programs demonstrate how to use the functions defined in the header files. Here's how each function is implemented and used:

#### MMIO Interface (`tcam_mmio.h`)

**Function Descriptions:**
- **`write_tcam(data, address)`**: Writes 32-bit data to the specified 28-bit address in TCAM
- **`write_tcam_array(data_array)`**: Writes an entire array to TCAM sequentially starting from address 0
- **`search_tcam(search_query)`**: Performs a search operation using the query value as the search key
- **`read_tcam_status()`**: Reads the TCAM status register to get the priority match address (PMA)

**Usage Examples from Test Programs:**

**tcam_32x28.c:**
```c
// Bulk array writing - writes 513 elements sequentially
uint32_t smem[513] = { /* test data array */ };
write_tcam_array(smem);  // Automatically writes to addresses 0-512

// Search operation
uint32_t search_query = 0x0FFC3201; 
search_tcam(search_query);

// Read results
printf("TCAM read status: 0x%08X\n", read_tcam_status());
```

**tcam_64x28.c:**
```c
// Individual writes with specific addresses
write_tcam(0x00000010, 0x00000005);  // data=0x00000005, address=0x00000010
write_tcam(0x00000000, 0x00000085);  // data=0x00000085, address=0x00000000
write_tcam(0x00000010, 0x00000105);  // data=0x00000105, address=0x00000010

// Search operation
uint32_t search_query = 0x00A14285; 
search_tcam(search_query);
printf("TCAM read status: 0x%08X\n", read_tcam_status());
```

#### ROCC Interface (`tcam_rocc.h`)

**Function Descriptions:**
- **`write_tcam(tcam_addr, wdata)`**: Writes data to TCAM using ROCC custom instructions (funct=0)
- **`search_tcam(query)`**: Performs search operation using ROCC custom instructions (funct=2)
- **`read_tcam_status()`**: Reads TCAM match status using ROCC custom instructions (funct=3)
- **`write_tcam_array(data_array)`**: Writes an entire array to TCAM sequentially using ROCC instructions

**Usage Examples from Test Programs:**

**tcam_32x28rocc.c:**
```c
// Same bulk array writing as MMIO version
uint32_t smem[513] = { /* identical test data */ };
write_tcam_array(smem);  // Uses ROCC custom instructions internally

// Search operation via ROCC
uint32_t search_query = 0x0FFC3201; 
search_tcam(search_query);

// Read results via ROCC
printf("TCAM read status: 0x%08X\n", read_tcam_status());
```

**tcam_64x28rocc.c:**
```c
// Individual writes using ROCC custom instructions
write_tcam(0x00000005, 0x00000010);  // tcam_addr=0x00000005, wdata=0x00000010
write_tcam(0x00000085, 0x00000000);  // tcam_addr=0x00000085, wdata=0x00000000
write_tcam(0x00000105, 0x00000010);  // tcam_addr=0x00000105, wdata=0x00000010

// Search operation via ROCC
uint32_t search_query = 0x00A14285;
search_tcam(search_query);
printf("TCAM match status: 0x%08X\n", read_tcam_status());
```



### Test Configuration

#### MMIO Test Configuration
```c
// Use these configurations for MMIO testing
class Tcam32x28Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM32x28TL ++
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

class Tcam64x28Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM64x28TL ++
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.AbstractConfig)
```

#### ROCC Test Configuration
```c
// Use these configurations for ROCC testing
class TCAMRoCC32x28Config extends Config(
  new tcam.WithTCAMRoCC32x28 ++
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++
  new chipyard.config.AbstractConfig)

class TCAMRoCC64x28Config extends Config(
  new tcam.WithTCAMRoCC64x28 ++
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++
  new chipyard.config.AbstractConfig)
```

### Building and Running Tests

The TCAM generator uses configurations defined in Chipyard's config files:
- **MMIO Configurations**: Defined in `generators/chipyard/src/main/scala/config/MMIOAcceleratorConfigs.scala`
- **ROCC Configurations**: Defined in `generators/chipyard/src/main/scala/config/RoCCAcceleratorConfigs.scala`

#### 1. **Build and Run MMIO Tests**

**Build the design:**
```bash
cd sims/verilator
make CONFIG=Tcam32x28Config
make CONFIG=Tcam64x28Config
```

**Run MMIO tests:**
```bash
# Run 32x28 MMIO test
make CONFIG=Tcam32x28Config BINARY=../../tests/tcam_32x28.riscv run-binary

# Run 64x28 MMIO test  
make CONFIG=Tcam64x28Config BINARY=../../tests/tcam_64x28.riscv run-binary
```

#### 2. **Build and Run ROCC Tests**

**Build the design:**
```bash
cd sims/verilator
make CONFIG=TCAMRoCC32x28Config
make CONFIG=TCAMRoCC64x28Config
```

**Run ROCC tests:**
```bash
# Run 32x28 ROCC test
make CONFIG=TCAMRoCC32x28Config BINARY=../../tests/tcam_32x28rocc.riscv run-binary

# Run 64x28 ROCC test
make CONFIG=TCAMRoCC64x28Config BINARY=../../tests/tcam_64x28rocc.riscv run-binary
```

#### 3. **Configuration Details**

**MMIO Configurations (from MMIOAcceleratorConfigs.scala):**
```scala
class Tcam64x28Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM64x28TL ++  // 64x28 configuration
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

class Tcam32x28Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM32x28TL ++  // 32x28 configuration
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.AbstractConfig)
```

**ROCC Configurations (from RoCCAcceleratorConfigs.scala):**
```scala
class TCAMRoCC64x28Config extends Config(
  new tcam.WithTCAMRoCC64x28 ++
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++
  new chipyard.config.AbstractConfig)
  
class TCAMRoCC32x28Config extends Config(
  new tcam.WithTCAMRoCC32x28 ++
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++
  new chipyard.config.AbstractConfig)
```



## Getting Started

1. Clone and set up Chipyard following the [official Chipyard documentation](https://chipyard.readthedocs.io/)
2. Clone this repository into your Chipyard workspace
3. **Write your TCAM test program** using the functions defined in `tcam_mmio.h` for MMIO interface or `tcam_rocc.h` for ROCC interface to test your TCAM functionality
4. **Test your TCAM** by entering required search queries and verifying the results using the appropriate test commands

## Dependencies

- Chipyard framework
- Chisel3
- Rocket Chip
- OpenRAM (for SRAM models)

## Usage

### ROCC Usage
```assembly
# Write operation: funct=0
custom0 x1, x2, x3, 0    # Write data from x3 to address in x2, mask in x2[31:28]

# Search operation: funct=2  
custom0 x1, x2, x3, 2    # Search for pattern in x3 at address in x2

# Status operation: funct=3
custom0 x1, x2, x3, 3    # Get match status in x1
```

### MMIO Usage
```c
// Control register bits
#define TCAM_CSB_BIT    0    // Chip select (inverted)
#define TCAM_WEB_BIT    1    // Write enable (inverted)
#define TCAM_WMASK_BITS 4-7  // Write mask

// Example: Write data
*(volatile uint32_t*)(TCAM_BASE + 0x08) = data;           // Write data
*(volatile uint32_t*)(TCAM_BASE + 0x0C) = address;        // Set address
*(volatile uint8_t*)(TCAM_BASE + 0x04) = 0x0F;            // Enable write, chip select
```


