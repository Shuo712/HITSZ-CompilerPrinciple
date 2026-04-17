package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.LRTable;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.parser.table.Term;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

//TODO: 实验二: 实现 LR 语法分析驱动程序

/**
 * LR 语法分析驱动程序
 * <br>
 * 该程序接受词法单元串与 LR 分析表 (action 和 goto 表), 按表对词法单元流进行分析, 执行对应动作, 并在执行动作时通知各注册的观察者.
 * <br>
 * 你应当按照被挖空的方法的文档实现对应方法, 你可以随意为该类添加你需要的私有成员对象, 但不应该再为此类添加公有接口, 也不应该改动未被挖空的方法,
 * 除非你已经同助教充分沟通, 并能证明你的修改的合理性, 且令助教确定可能被改动的评测方法. 随意修改该类的其它部分有可能导致自动评测出错而被扣分.
 */
public class SyntaxAnalyzer {
    /**
     * 符号表
     */
    private final SymbolTable symbolTable;

    /**
     * 观察者列表
     */
    private final List<ActionObserver> observers = new ArrayList<>();

    /**
     * 词法分析器输出的token列表
     */
    private final List<Token> tokenList = new ArrayList<>();
    /**
     * 当前读取的token下标
     */
    private int index = 0;

    /**
     * LR(1) 分析表。
     */
    private LRTable lrTable;

    /**
     * LR(1) 分析表的初始状态。
     */
    private Status initStatus;


    public SyntaxAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 注册新的观察者
     *
     * @param observer 观察者
     */
    public void registerObserver(ActionObserver observer) {
        observers.add(observer);
        observer.setSymbolTable(symbolTable);
    }

    /**
     * 在执行 shift 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param currentToken  当前词法单元
     */
    public void callWhenInShift(Status currentStatus, Token currentToken) {
        for (final var listener : observers) {
            listener.whenShift(currentStatus, currentToken);
        }
    }

    /**
     * 在执行 reduce 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param production    待规约的产生式
     */
    public void callWhenInReduce(Status currentStatus, Production production) {
        for (final var listener : observers) {
            listener.whenReduce(currentStatus, production);
        }
    }

    /**
     * 在执行 accept 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     */
    public void callWhenInAccept(Status currentStatus) {
        for (final var listener : observers) {
            listener.whenAccept(currentStatus);
        }
    }

    public void loadTokens(Iterable<Token> tokens) {
        // TODO: 加载词法单元
        // 你可以自行选择要如何存储词法单元, 譬如使用迭代器, 或是栈, 或是干脆使用一个 list 全存起来
        // 需要注意的是, 在实现驱动程序的过程中, 你会需要面对只读取一个 token 而不能消耗它的情况,
        // 在自行设计的时候请加以考虑此种情况
        tokenList.clear();
        for (final var token : tokens) {
            tokenList.add(token);
        }
        index = 0;
    }

    public void loadLRTable(LRTable table) {
        // TODO: 加载 LR 分析表
        // 你可以自行选择要如何使用该表格:
        // 是直接对 LRTable 调用 getAction/getGoto, 抑或是直接将 initStatus 存起来使用
        this.lrTable = table;
        this.initStatus = table.getInit();
    }

    public void run() {
        // TODO: 实现驱动程序
        // 你需要根据上面的输入来实现 LR 语法分析的驱动程序
        // 请分别在遇到 Shift, Reduce, Accept 的时候调用上面的 callWhenInShift, callWhenInReduce, callWhenInAccept
        // 否则用于为实验二打分的产生式输出可能不会正常工作

        // 状态栈
        final Deque<Status> statusStack = new ArrayDeque<>();
        // 符号栈
        final Deque<Term> symbolStack = new ArrayDeque<>();
        // 初始化栈
        statusStack.push(initStatus);

        // 循环分析
        while (true) {
            // 取状态和符号
            final Status currentStatus = statusStack.peek();
            final Token currentToken = tokenList.get(index);

            // 查询ACTION表
            final var action = lrTable.getAction(currentStatus, currentToken);

            // 执行动作分支
            switch (action.getKind()) {
                // 移进
                case Shift -> {
                    // 通知观察者
                    callWhenInShift(currentStatus, currentToken);
                    // 符号栈压入当前符号类型
                    symbolStack.push(currentToken.getKind());
                    // 状态栈压入移进后的目标状态
                    statusStack.push(action.getStatus());
                    // 消耗当前 token，指针后移
                    index++;
                }
                // 归约
                case Reduce -> {
                    final Production production = action.getProduction();
                    final int bodySize = production.body().size();

                    // 按产生式右部长度同时弹出状态栈和符号栈中的对应元素
                    for (int i = 0; i < bodySize; i++) {
                        if (statusStack.isEmpty() || symbolStack.isEmpty()) {
                            throw new RuntimeException("Parser stack underflow while reducing: " + production);
                        }
                        statusStack.pop();
                        symbolStack.pop();
                    }

                    // 弹栈后，新的栈顶状态才是查询 GOTO 的依据
                    final Status gotoFromStatus = statusStack.peek();
                    if (gotoFromStatus == null) {
                        throw new RuntimeException("No status left in stack after reducing: " + production);
                    }

                    // 通知观察者
                    callWhenInReduce(gotoFromStatus, production);

                    // 根据 GOTO 表得到归约后的新状态
                    final Status gotoStatus = lrTable.getGoto(gotoFromStatus, production.head());
                    if (gotoStatus.isError()) {
                        throw new RuntimeException(
                                "Goto error after reducing %s from state %s".formatted(production, gotoFromStatus)
                        );
                    }

                    // 符号栈压入产生式左部非终结符，状态栈压入 GOTO 得到的新状态
                    symbolStack.push(production.head());
                    statusStack.push(gotoStatus);
                }
                // 接受
                case Accept -> {
                    // 输入串被成功归约到开始符号，分析结束。
                    callWhenInAccept(currentStatus);
                    return;
                }
                // 错误
                case Error -> throw new RuntimeException(
                        "Syntax error at token %s when state is %s".formatted(currentToken, currentStatus)
                );
            }
        }
    }
}
