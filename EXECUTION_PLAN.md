# Runtime Analysis DSL & API Implementation Roadmap

## Overview
This document outlines the APIs and DSL capabilities to be implemented for the Auto DBG plugin's runtime awareness system. Items are organized by implementation priority, starting with deterministic functions that provide exact runtime data.

## Phase 1: Core Deterministic APIs ✅ High Priority

### Frame Analysis APIs
- [ ] `getFrameAt(depth: Int): FrameInfo`
  - **Description**: Retrieve stack frame information at specific depth
  - **Returns**: Frame details including method name, line number, file path
  - **Usage**: `GET frame AT depth:2`

- [ ] `getCallStack(maxDepth: Int = 10): List<FrameInfo>`
  - **Description**: Get complete call stack up to specified depth
  - **Returns**: Ordered list of frames from current to root
  - **Usage**: `GET callstack WITH depth:10`

- [ ] `getFrameVariables(frameId: String, maxDepth: Int = 3): Map<String, Variable>`
  - **Description**: Extract all variables visible in specified frame
  - **Returns**: Variable name-value pairs with type information
  - **Usage**: `GET variables FROM frame:current WITH depth:3`

- [ ] `getVariableValue(variableName: String, frameId: String): Any?`
  - **Description**: Get specific variable value from frame
  - **Returns**: Variable value or null if not found
  - **Usage**: `GET variable:"userId" FROM frame:current`

- [ ] `getVariableType(variableName: String, frameId: String): String`
  - **Description**: Get variable type information
  - **Returns**: Fully qualified type name
  - **Usage**: `GET type OF variable:"users" FROM frame:current`

### Expression Evaluation APIs
- [ ] `evaluateExpression(expression: String, frameId: String): EvaluationResult`
  - **Description**: Execute code expression in frame context
  - **Returns**: Expression result with type and value
  - **Usage**: `EXECUTE "users.size()" IN frame:current`

- [ ] `searchVariables(pattern: String, scope: SearchScope): List<Variable>`
  - **Description**: Find variables matching name pattern
  - **Returns**: List of matching variables across specified scope
  - **Usage**: `FIND variables WHERE name MATCHES "*user*" IN scope:current_frame`

## Phase 2: Memory Analysis APIs ⚠️ Medium Priority

### Object Reference APIs
- [ ] `getObjectReferences(objectId: String): List<Reference>`
  - **Description**: Find all objects referencing the specified object
  - **Returns**: List of reference information including field names and types
  - **Usage**: `GET references TO object:"obj_12345"`

- [ ] `analyzeHeapUsage(): HeapSummary`
  - **Description**: Get current heap memory statistics
  - **Returns**: Total/used/free heap, top classes by memory usage
  - **Usage**: `ANALYZE heap`

### Thread Analysis APIs
- [ ] `getThreadStates(): Map<String, ThreadInfo>`
  - **Description**: Get current state of all threads
  - **Returns**: Thread name mapped to state (RUNNING, WAITING, etc.)
  - **Usage**: `GET threads`

- [ ] `getThreadCallStacks(): Map<String, List<FrameInfo>>`
  - **Description**: Get call stack for each thread
  - **Returns**: Thread name mapped to its call stack
  - **Usage**: `GET callstacks FOR all_threads`

## Phase 3: Control Flow APIs 🔄 Medium Priority

### Execution Tracing APIs
- [ ] `traceVariableFlow(variableName: String, fromFrame: Int, toFrame: Int): FlowTrace`
  - **Description**: Trace how variable value changes through call stack
  - **Returns**: Variable value at each frame in the range
  - **Usage**: `TRACE variable:"userId" FROM frame:0 TO frame:5`

- [ ] `getExecutionPath(maxSteps: Int = 100): List<ExecutionStep>`
  - **Description**: Get recent execution steps leading to current state
  - **Returns**: Ordered list of method calls and line executions
  - **Usage**: `GET execution_path WITH steps:50`

### Breakpoint Management APIs
- [ ] `setConditionalBreakpoint(location: String, condition: String): BreakpointId`
  - **Description**: Set breakpoint that triggers when condition is true
  - **Returns**: Unique breakpoint identifier
  - **Usage**: `SET breakpoint AT "User.java:45" WHEN "user.id == null"`

## Phase 4: DSL Parser & Executor 🔧 Core Infrastructure

### DSL Components
- [ ] **Tokenizer**: Split DSL commands into tokens
- [ ] **Parser**: Convert tokens into command objects
- [ ] **Validator**: Ensure command syntax and semantics are correct
- [ ] **Executor**: Execute parsed commands against debug APIs
- [ ] **Response Optimizer**: Compress results for token efficiency

### DSL Grammar Examples
```
# Basic queries
GET variables FROM frame:current
GET frame AT depth:2
FIND variables WHERE type CONTAINS "List"

# Complex analysis
TRACE variable:"userId" FROM frame:current TO frame:root
EXECUTE "users.stream().filter(u -> u.id == null).count()" IN frame:current
DETECT pattern:"null_risks" IN scope:current_thread

# Conditional operations
GET variables WHERE value IS NULL AND depth <= 2
FIND objects WHERE size > 1000 IN frames[0..5]
```

## Implementation Notes

1. **Start with Phase 1** - These provide the foundation for all other features
2. **Each API should include**:
   - Input validation
   - Error handling
   - Token usage estimation
   - Result caching where appropriate
3. **DSL should be**:
   - Simple and intuitive
   - Easily extensible
   - Well-documented with examples
4. **All functions should**:
   - Handle edge cases gracefully
   - Provide meaningful error messages
   - Include performance metrics

## Success Metrics

- [ ] LLM can debug issues using only DSL commands (no raw data)
- [ ] Token usage reduced by >70% compared to raw data approach
- [ ] Response time <2 seconds for most deterministic queries
- [ ] Support for complex multi-step debugging workflows
