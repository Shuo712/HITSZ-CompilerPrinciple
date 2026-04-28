package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: 实验三: 实现 IR 生成
public class IRGenerator implements ActionObserver {

    private final Deque<IRAttribute> irStack = new ArrayDeque<>();
    private final List<Instruction> instructions = new ArrayList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO
        final String kindId = currentToken.getKindId();
        final IRAttribute attribute = switch (kindId) {
            case "id" -> IRAttribute.forPlace(IRVariable.named(currentToken.getText()));
            case "IntConst" -> IRAttribute.forPlace(IRImmediate.of(Integer.parseInt(currentToken.getText())));
            default -> IRAttribute.empty();
        };

        irStack.push(attribute);
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO
        final List<IRAttribute> bodyAttributes = popBodyAttributes(production.body().size());
        final IRAttribute result = switch (production.index()) {
            case 5 -> reduceAssign(bodyAttributes);          // S -> id = E
            case 6 -> reduceReturn(bodyAttributes);          // S -> return E
            case 10 -> reduceAdd(bodyAttributes);            // E -> E + A
            case 11 -> reduceSub(bodyAttributes);            // E -> E - A
            case 12 -> reducePass(bodyAttributes, 0);        // E -> A
            case 13 -> reduceMul(bodyAttributes);            // A -> A * B
            case 14 -> reduceDiv(bodyAttributes);            // A -> A / B
            case 15 -> reducePass(bodyAttributes, 0);        // A -> B
            case 16 -> reducePass(bodyAttributes, 1);        // B -> ( E )
            case 17 -> reducePass(bodyAttributes, 0);        // B -> id
            case 18 -> reducePass(bodyAttributes, 0);        // B -> IntConst
            default -> IRAttribute.empty();
        };

        irStack.push(result);
    }


    @Override
    public void whenAccept(Status currentStatus) {
        // TODO
        // 无需操作
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO
    }

    public List<Instruction> getIR() {
        // TODO
        return List.copyOf(instructions);
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }

    /**
     * 根据右部长度弹出 IR 属性，并按文法从左到右的顺序返回。
     *
     * @param bodySize 产生式右部长度
     * @return 右部各符号属性列表，顺序与产生式右部一致
     */
    private List<IRAttribute> popBodyAttributes(int bodySize) {
        final List<IRAttribute> attributes = new ArrayList<>(bodySize);
        for (int i = 0; i < bodySize; i++) {
            attributes.add(0, irStack.pop());
        }
        return attributes;
    }

    /**
     * 处理赋值语句 S -> id = E，生成 MOV 指令。
     *
     * @param bodyAttributes 右部属性列表
     * @return 语句规约后的占位属性
     */
    private IRAttribute reduceAssign(List<IRAttribute> bodyAttributes) {
        final IRVariable target = ensureVariable(bodyAttributes.get(0).place());
        final IRValue source = requirePlace(bodyAttributes.get(2));
        instructions.add(Instruction.createMov(target, source));
        return IRAttribute.empty();
    }

    /**
     * 处理返回语句 S -> return E，生成 RET 指令。
     *
     * @param bodyAttributes 右部属性列表
     * @return 语句规约后的占位属性
     */
    private IRAttribute reduceReturn(List<IRAttribute> bodyAttributes) {
        final IRValue returnValue = requirePlace(bodyAttributes.get(1));
        instructions.add(Instruction.createRet(returnValue));
        return IRAttribute.empty();
    }

    /**
     * 处理加法表达式 E -> E + A，生成 ADD 指令。
     *
     * @param bodyAttributes 右部属性列表
     * @return 规约后表达式的结果属性
     */
    private IRAttribute reduceAdd(List<IRAttribute> bodyAttributes) {
        final IRValue lhs = requirePlace(bodyAttributes.get(0));
        final IRValue rhs = requirePlace(bodyAttributes.get(2));
        final IRVariable temp = IRVariable.temp();
        instructions.add(Instruction.createAdd(temp, lhs, rhs));
        return IRAttribute.forPlace(temp);
    }

    /**
     * 处理减法表达式 E -> E - A，生成 SUB 指令。
     *
     * @param bodyAttributes 右部属性列表
     * @return 规约后表达式的结果属性
     */
    private IRAttribute reduceSub(List<IRAttribute> bodyAttributes) {
        final IRValue lhs = requirePlace(bodyAttributes.get(0));
        final IRValue rhs = requirePlace(bodyAttributes.get(2));
        final IRVariable temp = IRVariable.temp();
        instructions.add(Instruction.createSub(temp, lhs, rhs));
        return IRAttribute.forPlace(temp);
    }

    /**
     * 处理乘法表达式 A -> A * B，生成 MUL 指令。
     *
     * @param bodyAttributes 右部属性列表
     * @return 规约后表达式的结果属性
     */
    private IRAttribute reduceMul(List<IRAttribute> bodyAttributes) {
        final IRValue lhs = requirePlace(bodyAttributes.get(0));
        final IRValue rhs = requirePlace(bodyAttributes.get(2));
        final IRVariable temp = IRVariable.temp();
        instructions.add(Instruction.createMul(temp, lhs, rhs));
        return IRAttribute.forPlace(temp);
    }

    /**
     * 处理除法表达式 A -> A / B，生成 DIV 指令。
     *
     * @param bodyAttributes 右部属性列表
     * @return 规约后表达式的结果属性
     */
    private IRAttribute reduceDiv(List<IRAttribute> bodyAttributes) {
        final IRValue lhs = requirePlace(bodyAttributes.get(0));
        final IRValue rhs = requirePlace(bodyAttributes.get(2));
        final IRVariable temp = IRVariable.temp();
        instructions.add(Instruction.createDiv(temp, lhs, rhs));
        return IRAttribute.forPlace(temp);
    }

    /**
     * 处理单纯属性上传的产生式，如 E -> A、A -> B、B -> ( E ) 等。
     *
     * @param bodyAttributes 右部属性列表
     * @param indexToPass    需要上传 place 的右部位置
     * @return 规约后上传得到的属性
     */
    private IRAttribute reducePass(List<IRAttribute> bodyAttributes, int indexToPass) {
        return IRAttribute.forPlace(requirePlace(bodyAttributes.get(indexToPass)));
    }

    /**
     * 从属性项中提取 place，并检查其不能为空。
     *
     * @param attribute 属性项
     * @return 对应的 IRValue
     */
    private IRValue requirePlace(IRAttribute attribute) {
        if (attribute.place() == null) {
            throw new RuntimeException("IR attribute place is null");
        }
        return attribute.place();
    }

    /**
     * 将 IRValue 检查并转换为 IRVariable。
     *
     * @param value 待检查的 IRValue
     * @return 对应的 IRVariable
     */
    private IRVariable ensureVariable(IRValue value) {
        if (value instanceof IRVariable variable) {
            return variable;
        }
        throw new RuntimeException("Assignment target must be a variable");
    }

    /**
     * IR 属性项。
     * <p>
     * 该结构用于记录终结符或非终结符在 IR 生成阶段对应的 place 属性。
     */
    private record IRAttribute(IRValue place) {
        /**
         * 创建一个空属性项，用作无 IR 值符号的占位。
         *
         * @return 空属性项
         */
        private static IRAttribute empty() {
            return new IRAttribute(null);
        }

        /**
         * 创建一个带 place 值的属性项。
         *
         * @param place 当前符号对应的 IR 位置
         * @return 带 place 的属性项
         */
        private static IRAttribute forPlace(IRValue place) {
            return new IRAttribute(place);
        }
    }
}

