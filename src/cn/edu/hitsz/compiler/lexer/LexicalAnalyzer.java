package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 缓冲区
     */
    private String buf;

    /**
     * token列表
     */
    private List<Token> tokenList;


    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // TODO: 词法分析前的缓冲区实现
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法
        buf = FileUtils.readFile(path);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // TODO: 自动机实现的词法分析过程
        // 初始化
        tokenList = new ArrayList<>();
        int index = 0;
        // 词法分析
        while (index < buf.length()) {
            // 读取字符
            char c = buf.charAt(index);
            // 跳过空白字符
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }

            // 识别标识符/关键字（letter开头：字母 + _）
            if (Character.isLetter(c) || c == '_') {
                int start = index;  // 记录起始位置
                index++;
                // 读取word
                while (index < buf.length()) {
                    char next = buf.charAt(index);
                    if (Character.isLetter(next) || Character.isDigit(next) || next == '_') {
                        index++;
                    } else {
                        break;
                    }
                }
                String word = buf.substring(start, index);

                if(word.equals("int") || word.equals("return")){
                    // 关键字
                    tokenList.add(Token.simple(word));
                }else{
                    // 标识符
                    tokenList.add(Token.normal("id", word));
                    // 如果该标识符不在符号表中，则添加
                    if(!symbolTable.has(word)){
                        symbolTable.add(word);
                    }
                }
                continue;
            }

            // 整常数（首字符为1~9）
            if(c >= '1' && c <= '9'){
                int start = index;  // 记录起始位置
                index++;
                // 读取数字
                while (index < buf.length() && Character.isDigit(buf.charAt(index))) {
                    index++;
                }
                String number = buf.substring(start, index);
                tokenList.add(Token.normal("IntConst", number));
                continue;
            }

            // 运算符和分隔符
            switch (c) {
                case '=':
                    tokenList.add(Token.simple("="));
                    index++;
                    break;
                case '*':
                    tokenList.add(Token.simple("*"));
                    index++;
                    break;
                case '+':
                    tokenList.add(Token.simple("+"));
                    index++;
                    break;
                case '-':
                    tokenList.add(Token.simple("-"));
                    index++;
                    break;
                case '/':
                    tokenList.add(Token.simple("/"));
                    index++;
                    break;
                case '(':
                    tokenList.add(Token.simple("("));
                    index++;
                    break;
                case ')':
                    tokenList.add(Token.simple(")"));
                    index++;
                    break;
                case ',':
                    tokenList.add(Token.simple(","));
                    index++;
                    break;
                case ';':
                    tokenList.add(Token.simple("Semicolon"));
                    index++;
                    break;
                default:
                    // 错误
                    throw new RuntimeException("Illegal character: " + c);
            }
        }

        // 添加结束符
        tokenList.add(Token.eof());
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // TODO: 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
        if (tokenList == null) {
            throw new RuntimeException("Tokens have not been generated");
        }
        return tokenList;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
                path,
                StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }


}
