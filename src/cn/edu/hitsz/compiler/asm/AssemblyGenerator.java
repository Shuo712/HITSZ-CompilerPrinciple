package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.ir.InstructionKind;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {
    private static final List<String> REGISTERS = List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6");

    private final List<Instruction> instructions = new ArrayList<>();
    private final List<String> asmLines = new ArrayList<>();
    private final Map<IRVariable, String> varToReg = new HashMap<>();
    private final Map<String, IRVariable> regToVar = new HashMap<>();
    private final Map<IRVariable, Integer> lastUsage = new HashMap<>();

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        instructions.clear();
        asmLines.clear();
        varToReg.clear();
        regToVar.clear();
        lastUsage.clear();

        for (final var instruction : originInstructions) {
            normalizeInstruction(instruction);
        }

        collectLastUsage();
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        asmLines.clear();
        varToReg.clear();
        regToVar.clear();
        asmLines.add(".text");

        for (int index = 0; index < instructions.size(); index++) {
            final var instruction = instructions.get(index);
            switch (instruction.getKind()) {
                case MOV -> genMov(index, instruction);
                case ADD, SUB, MUL, DIV -> genBinary(index, instruction);
                case RET -> {
                    genReturn(index, instruction);
                    return;
                }
            }

            releaseDeadVariables(index);
        }
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        FileUtils.writeLines(path, asmLines);
    }

    /**
     * 将一条 IR 规范化为更容易生成 RISC-V 汇编的形式。
     *
     * @param instruction 原始 IR 指令
     */
    private void normalizeInstruction(Instruction instruction) {
        if (!instruction.getKind().isBinary()) {
            instructions.add(instruction);
            return;
        }

        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();

        if (lhs instanceof IRImmediate lhsImm && rhs instanceof IRImmediate rhsImm) {
            instructions.add(Instruction.createMov(result, IRImmediate.of(calculateImmediate(instruction.getKind(), lhsImm.getValue(), rhsImm.getValue()))));
            return;
        }

        switch (instruction.getKind()) {
            case ADD -> normalizeAdd(result, lhs, rhs);
            case SUB -> normalizeSub(result, lhs, rhs);
            case MUL, DIV -> normalizeRegOnlyBinary(instruction.getKind(), result, lhs, rhs);
            default -> instructions.add(instruction);
        }
    }

    /**
     * 规范化 ADD 指令，保证立即数最多出现在右操作数位置。
     *
     * @param result 结果变量
     * @param lhs    左操作数
     * @param rhs    右操作数
     */
    private void normalizeAdd(IRVariable result, IRValue lhs, IRValue rhs) {
        if (lhs instanceof IRImmediate && rhs instanceof IRVariable) {
            instructions.add(Instruction.createAdd(result, rhs, lhs));
        } else {
            instructions.add(Instruction.createAdd(result, lhs, rhs));
        }
    }

    /**
     * 规范化 SUB 指令，左立即数需要先搬入临时变量。
     *
     * @param result 结果变量
     * @param lhs    左操作数
     * @param rhs    右操作数
     */
    private void normalizeSub(IRVariable result, IRValue lhs, IRValue rhs) {
        if (lhs instanceof IRImmediate lhsImm) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createMov(temp, lhsImm));
            instructions.add(Instruction.createSub(result, temp, rhs));
        } else {
            instructions.add(Instruction.createSub(result, lhs, rhs));
        }
    }

    /**
     * 规范化必须使用寄存器操作数的二元指令。
     *
     * @param kind   指令种类
     * @param result 结果变量
     * @param lhs    左操作数
     * @param rhs    右操作数
     */
    private void normalizeRegOnlyBinary(InstructionKind kind, IRVariable result, IRValue lhs, IRValue rhs) {
        final var normalizedLhs = moveImmediateToTempIfNeeded(lhs);
        final var normalizedRhs = moveImmediateToTempIfNeeded(rhs);

        if (kind == InstructionKind.MUL) {
            instructions.add(Instruction.createMul(result, normalizedLhs, normalizedRhs));
        } else if (kind == InstructionKind.DIV) {
            instructions.add(Instruction.createDiv(result, normalizedLhs, normalizedRhs));
        }
    }

    /**
     * 如果操作数是立即数，将其转换为临时变量并插入 MOV 指令。
     *
     * @param value 原操作数
     * @return 可作为寄存器操作数使用的 IR 值
     */
    private IRValue moveImmediateToTempIfNeeded(IRValue value) {
        if (value instanceof IRImmediate immediate) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createMov(temp, immediate));
            return temp;
        }

        return value;
    }

    /**
     * 计算双立即数二元运算的折叠结果。
     *
     * @param kind 指令种类
     * @param lhs  左立即数
     * @param rhs  右立即数
     * @return 常量折叠后的结果
     */
    private int calculateImmediate(InstructionKind kind, int lhs, int rhs) {
        return switch (kind) {
            case ADD -> lhs + rhs;
            case SUB -> lhs - rhs;
            case MUL -> lhs * rhs;
            case DIV -> lhs / rhs;
            default -> throw new IllegalArgumentException("Unsupported immediate calculation: " + kind);
        };
    }

    /**
     * 统计每个变量最后一次作为右值或返回值出现的位置。
     */
    private void collectLastUsage() {
        for (int index = 0; index < instructions.size(); index++) {
            final var instruction = instructions.get(index);

            for (final var operand : instruction.getOperands()) {
                if (operand instanceof IRVariable variable) {
                    lastUsage.put(variable, index);
                }
            }
        }
    }

    /**
     * 为 MOV 指令生成汇编代码。
     *
     * @param index       当前 IR 下标
     * @param instruction MOV 指令
     */
    private void genMov(int index, Instruction instruction) {
        final var result = instruction.getResult();
        final var resultReg = allocateRegister(result, index);
        final var from = instruction.getFrom();

        if (from instanceof IRImmediate immediate) {
            emit("li %s, %d", resultReg, immediate.getValue(), instruction);
        } else if (from instanceof IRVariable variable) {
            final var fromReg = ensureRegister(variable, index);
            if (!resultReg.equals(fromReg)) {
                emit("mv %s, %s", resultReg, fromReg, instruction);
            }
        }
    }

    /**
     * 为二元运算指令生成汇编代码。
     *
     * @param index       当前 IR 下标
     * @param instruction 二元运算指令
     */
    private void genBinary(int index, Instruction instruction) {
        final var resultReg = allocateRegister(instruction.getResult(), index);
        final var lhsReg = ensureValueRegister(instruction.getLHS(), index);
        final var rhs = instruction.getRHS();

        if (rhs instanceof IRImmediate immediate) {
            genImmediateBinary(instruction, resultReg, lhsReg, immediate.getValue());
        } else if (rhs instanceof IRVariable variable) {
            final var rhsReg = ensureRegister(variable, index);
            genRegisterBinary(instruction, resultReg, lhsReg, rhsReg);
        }
    }

    /**
     * 为带右立即数的二元运算生成汇编代码。
     *
     * @param instruction 当前 IR 指令
     * @param resultReg   结果寄存器
     * @param lhsReg      左操作数寄存器
     * @param immediate   右立即数
     */
    private void genImmediateBinary(Instruction instruction, String resultReg, String lhsReg, int immediate) {
        switch (instruction.getKind()) {
            case ADD -> emit("addi %s, %s, %d", resultReg, lhsReg, immediate, instruction);
            case SUB -> emit("addi %s, %s, %d", resultReg, lhsReg, -immediate, instruction);
            default -> throw new IllegalArgumentException("Unsupported immediate binary: " + instruction);
        }
    }

    /**
     * 为纯寄存器二元运算生成汇编代码。
     *
     * @param instruction 当前 IR 指令
     * @param resultReg   结果寄存器
     * @param lhsReg      左操作数寄存器
     * @param rhsReg      右操作数寄存器
     */
    private void genRegisterBinary(Instruction instruction, String resultReg, String lhsReg, String rhsReg) {
        final var asmOperator = switch (instruction.getKind()) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            default -> throw new IllegalArgumentException("Unsupported register binary: " + instruction);
        };

        emit("%s %s, %s, %s", asmOperator, resultReg, lhsReg, rhsReg, instruction);
    }

    /**
     * 为 RET 指令生成汇编代码。
     *
     * @param index       当前 IR 下标
     * @param instruction RET 指令
     */
    private void genReturn(int index, Instruction instruction) {
        final var returnValue = instruction.getReturnValue();

        if (returnValue instanceof IRImmediate immediate) {
            emit("li a0, %d", immediate.getValue(), instruction);
        } else if (returnValue instanceof IRVariable variable) {
            final var returnReg = ensureRegister(variable, index);
            emit("mv a0, %s", returnReg, instruction);
        }

        releaseDeadVariables(index);
    }

    /**
     * 确保一个 IR 值位于寄存器中。
     *
     * @param value IR 值
     * @param index 当前 IR 下标
     * @return 保存该值的寄存器
     */
    private String ensureValueRegister(IRValue value, int index) {
        if (value instanceof IRVariable variable) {
            return ensureRegister(variable, index);
        } else if (value instanceof IRImmediate immediate) {
            final var temp = IRVariable.temp();
            final var reg = allocateRegister(temp, index);
            emit("li %s, %d", reg, immediate.getValue(), null);
            return reg;
        }

        throw new IllegalArgumentException("Unknown IR value: " + value);
    }

    /**
     * 确保一个变量已经被分配寄存器。
     *
     * @param variable IR 变量
     * @param index    当前 IR 下标
     * @return 保存该变量的寄存器
     */
    private String ensureRegister(IRVariable variable, int index) {
        final var existedReg = varToReg.get(variable);
        if (existedReg != null) {
            return existedReg;
        }

        throw new IllegalStateException("Variable has no available value in register: " + variable + " at IR index " + index);
    }

    /**
     * 为结果变量分配一个寄存器。
     *
     * @param variable 需要保存结果的变量
     * @param index    当前 IR 下标
     * @return 分配得到的寄存器
     */
    private String allocateRegister(IRVariable variable, int index) {
        final var existedReg = varToReg.get(variable);
        if (existedReg != null) {
            return existedReg;
        }

        for (final var register : REGISTERS) {
            if (!regToVar.containsKey(register)) {
                bind(variable, register);
                return register;
            }
        }

        for (final var register : REGISTERS) {
            final var occupiedVariable = regToVar.get(register);
            if (!willBeUsedLater(occupiedVariable, index)) {
                unbind(occupiedVariable);
                bind(variable, register);
                return register;
            }
        }

        throw new IllegalStateException("No free register and spilling is not enabled at IR index " + index);
    }

    /**
     * 判断变量在当前指令之后是否还会使用。
     *
     * @param variable 目标变量
     * @param index    当前 IR 下标
     * @return 当前指令之后是否仍会使用
     */
    private boolean willBeUsedLater(IRVariable variable, int index) {
        return lastUsage.getOrDefault(variable, -1) > index;
    }

    /**
     * 将变量与寄存器建立双向绑定。
     *
     * @param variable IR 变量
     * @param register 物理寄存器名
     */
    private void bind(IRVariable variable, String register) {
        varToReg.put(variable, register);
        regToVar.put(register, variable);
    }

    /**
     * 解除变量与寄存器的绑定。
     *
     * @param variable IR 变量
     */
    private void unbind(IRVariable variable) {
        final var register = varToReg.remove(variable);
        if (register != null) {
            regToVar.remove(register);
        }
    }

    /**
     * 释放当前指令之后不再使用的变量寄存器。
     *
     * @param index 当前 IR 下标
     */
    private void releaseDeadVariables(int index) {
        final var variablesToRelease = varToReg.keySet().stream()
            .filter(variable -> !willBeUsedLater(variable, index))
            .toList();

        for (final var variable : variablesToRelease) {
            unbind(variable);
        }
    }

    /**
     * 输出一行带 IR 注释的汇编代码。
     *
     * @param format      汇编格式字符串
     * @param arg1        第一个汇编格式参数
     * @param arg2        第二个汇编格式参数
     * @param arg3        第三个汇编格式参数
     * @param instruction 对应的 IR 指令，可为空
     */
    private void emit(String format, Object arg1, Object arg2, Object arg3, Instruction instruction) {
        appendAsmLine(format.formatted(arg1, arg2, arg3), instruction);
    }

    /**
     * 输出一行带 IR 注释的汇编代码。
     *
     * @param format      汇编格式字符串
     * @param arg1        第一个汇编格式参数
     * @param arg2        第二个汇编格式参数
     * @param instruction 对应的 IR 指令，可为空
     */
    private void emit(String format, Object arg1, Object arg2, Instruction instruction) {
        appendAsmLine(format.formatted(arg1, arg2), instruction);
    }

    /**
     * 输出一行带 IR 注释的汇编代码。
     *
     * @param format      汇编格式字符串
     * @param arg1        汇编格式参数
     * @param instruction 对应的 IR 指令，可为空
     */
    private void emit(String format, Object arg1, Instruction instruction) {
        appendAsmLine(format.formatted(arg1), instruction);
    }

    /**
     * 输出一行带 IR 注释的汇编代码。
     *
     * @param format      汇编格式字符串
     * @param arg1        第一个汇编格式参数
     * @param arg2        第二个汇编格式参数
     * @param arg3        第三个汇编格式参数
     * @param arg4        第四个汇编格式参数
     * @param instruction 对应的 IR 指令，可为空
     */
    private void emit(String format, Object arg1, Object arg2, Object arg3, Object arg4, Instruction instruction) {
        appendAsmLine(format.formatted(arg1, arg2, arg3, arg4), instruction);
    }

    /**
     * 将汇编正文追加到输出列表。
     *
     * @param asm         汇编正文
     * @param instruction 对应的 IR 指令，可为空
     */
    private void appendAsmLine(String asm, Instruction instruction) {
        if (instruction == null) {
            asmLines.add("    " + asm);
        } else {
            asmLines.add("    " + asm + "\t\t#  " + instruction);
        }
    }
}
