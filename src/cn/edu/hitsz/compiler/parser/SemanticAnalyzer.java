package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.symtab.SymbolTableEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {

    private final Deque<SemanticAttribute> semanticStack = new ArrayDeque<>();
    private SymbolTable symbolTable;

    @Override
    public void whenAccept(Status currentStatus) {
        // TODO: 该过程在遇到 Accept 时要采取的代码动作
        // 无需操作
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO: 该过程在遇到 reduce production 时要采取的代码动作
        final List<SemanticAttribute> bodyAttributes = popBodyAttributes(production.body().size());
        final SemanticAttribute result = switch (production.index()) {
            case 4 -> reduceDeclaration(bodyAttributes);         // S -> D DeclList
            case 7 -> reduceTypeInt();                          // D -> int
            case 8 -> reduceDeclListWithComma(bodyAttributes);  // DeclList -> id , DeclList
            case 9 -> reduceDeclListSingle(bodyAttributes);     // DeclList -> id
            case 10, 11, 12, 13, 14, 15, 16, 17, 18 -> reduceExpression(bodyAttributes);
            default -> SemanticAttribute.empty();
        };

        semanticStack.push(result);
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO: 该过程在遇到 shift 时要采取的代码动作
        final String kindId = currentToken.getKindId();
        final SemanticAttribute attribute = switch (kindId) {
            case "int" -> SemanticAttribute.forType(SourceCodeType.Int);
            case "id" -> SemanticAttribute.forName(currentToken.getText());
            case "IntConst" -> SemanticAttribute.forType(SourceCodeType.Int);
            default -> SemanticAttribute.empty();
        };

        semanticStack.push(attribute);
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO: 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        this.symbolTable = table;
    }
    /**
     * 根据右部长度弹出语义属性，并按文法从左到右的顺序返回。
     *
     * @param bodySize 产生式右部长度
     * @return 右部各符号属性列表，顺序与产生式右部一致
     */
    private List<SemanticAttribute> popBodyAttributes(int bodySize) {
        final List<SemanticAttribute> attributes = new ArrayList<>(bodySize);
        for (int i = 0; i < bodySize; i++) {
            attributes.add(0, semanticStack.pop());
        }
        return attributes;
    }

    /**
     * 处理产生式 D -> int，对应类型属性上传。
     *
     * @return 规约后 D 的语义属性
     */
    private SemanticAttribute reduceTypeInt() {
        return SemanticAttribute.forType(SourceCodeType.Int);
    }

    /**
     * 处理产生式 DeclList -> id。
     *
     * @param bodyAttributes 右部属性列表
     * @return 含单个标识符的声明列表属性
     */
    private SemanticAttribute reduceDeclListSingle(List<SemanticAttribute> bodyAttributes) {
        final String name = bodyAttributes.get(0).name();
        return SemanticAttribute.forIdList(List.of(name));
    }

    /**
     * 处理产生式 DeclList -> id , DeclList。
     *
     * @param bodyAttributes 右部属性列表
     * @return 合并后的声明列表属性
     */
    private SemanticAttribute reduceDeclListWithComma(List<SemanticAttribute> bodyAttributes) {
        final String currentName = bodyAttributes.get(0).name();
        final List<String> tailList = bodyAttributes.get(2).idList();

        final List<String> merged = new ArrayList<>();
        merged.add(currentName);
        merged.addAll(tailList);

        return SemanticAttribute.forIdList(merged);
    }

    /**
     * 处理产生式 S -> D DeclList，将声明列表中的所有标识符类型写回符号表。
     *
     * @param bodyAttributes 右部属性列表
     * @return 语句 S 的占位属性
     */
    private SemanticAttribute reduceDeclaration(List<SemanticAttribute> bodyAttributes) {
        final SourceCodeType type = bodyAttributes.get(0).type();
        final List<String> idList = bodyAttributes.get(1).idList();

        for (final String name : idList) {
            final SymbolTableEntry entry = symbolTable.get(name);
            if (entry.getType() != null) {
                throw new RuntimeException("Duplicate declaration for identifier: " + name);
            }
            entry.setType(type);
        }

        return SemanticAttribute.empty();
    }

    /**
     * 处理表达式相关产生式，仅在语义层面上传 Int 类型，
     * 以保证表达式规约时语义栈结构完整。
     *
     * @param bodyAttributes 右部属性列表
     * @return 表达式规约后的语义属性
     */
    private SemanticAttribute reduceExpression(List<SemanticAttribute> bodyAttributes) {
        return SemanticAttribute.forType(SourceCodeType.Int);
    }

    /**
     * 语义属性项。
     * <p>
     * 该结构同时承担终结符与非终结符的语义属性记录工作。
     */
    private record SemanticAttribute(SourceCodeType type, String name, List<String> idList) {
        /**
         * 创建一个空属性项，用作无语义值符号的占位。
         *
         * @return 空语义属性
         */
        private static SemanticAttribute empty() {
            return new SemanticAttribute(null, null, List.of());
        }

        /**
         * 创建一个仅带类型信息的语义属性。
         *
         * @param type 语义类型
         * @return 类型属性项
         */
        private static SemanticAttribute forType(SourceCodeType type) {
            return new SemanticAttribute(type, null, List.of());
        }

        /**
         * 创建一个仅带标识符名称的语义属性。
         *
         * @param name 标识符名称
         * @return 名称属性项
         */
        private static SemanticAttribute forName(String name) {
            return new SemanticAttribute(null, name, List.of());
        }

        /**
         * 创建一个声明列表属性。
         *
         * @param idList 标识符列表
         * @return 声明列表属性项
         */
        private static SemanticAttribute forIdList(List<String> idList) {
            return new SemanticAttribute(null, null, idList);
        }
    }
}
