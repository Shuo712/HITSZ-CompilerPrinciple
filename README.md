# HITSZ 2026春编译原理实验

本学期的编译原理实验总共有4次实验，全部为设计型实验。 

- 实验一：词法分析器的实现； 
- 实验二：自底向上的语法分析—LR（1）； 
- 实验三：典型语句的语义分析及中间代码生成； 
- 实验四：目标代码生成。

<img width="819" height="532" alt="image" src="https://github.com/user-attachments/assets/e81aa618-d514-4aac-8a87-1bcd976ecd32" /> 

```
. 
├── data                 
│   ├── in   # 提供给程序的输入数据               
│   ├── out  # 程序的输出数据                
│   └── std  # 用作参考的标准输出数据                
├── scripts 
│   ├── check-result.py  # 对输出进行 check 的脚本    
│   ├── diff.py  # 忽略文件尾空行和行首位空白符的 diff 工具              
│   └── make-template.py  # 从代码出框架的脚本, 无需关注     
└── src  # 源码目录                     
```
```
src/cn/edu/hitsz/compiler 
├── asm                             # 实验四: 汇编生成 
│   └── AssemblyGenerator.java          # 汇编生成器 
├── ir                              # 基础架构: IR 
│   ├── BasicBlock.java                 # IR 中的基本块 
│   ├── Instruction.java                # IR 中的指令 
│   ├── InstructionKind.java            # IR 中的指令类型 
│   ├── IRImmediate.java                # IR 中的立即数 
│   ├── IRValue.java                    # IR 中的值 
│   ├── IRVariable.java                 # IR 中的变量 
│   └── Program.java                    # IR 中代表完整的一个程序的类 
├── lexer                           # 实验一: 词法分析 
│   ├── LexicalAnalyzer.java            # 词法分析器 
│   ├── Token.java                      # 词法单元 
│   └── TokenKind.java                  # 词法单元类别 
├── parser                          # 实验二/三: 语法分析, 语义分析, IR 生成 
│   ├── table                           # 读取/生成 LR 表的工具 
│   ├── ActionObserver.java             # 观察者接口 
│   ├── IRGenerator.java                # IR 生成 
│   ├── ProductionCollector.java        # 规约产生式记录 
│   ├── SemanticAnalyzer.java           # 语义分析 
│   └── SyntaxAnalyzer.java             # 语法分析 
├── pass                            # 可选部分: 优化 pass 
│   ├── AdjustIRForRISCV.java           # 实验四 (可选部分): 体系结构相关优化与 
IR 调整 
│   ├── IRPass.java                     # 所有 IR 优化 pass 的基类 
│   └── IRPassManager.java              # IR 优化 pass 管理器 
├── symtab                          # 符号表 
│   ├── SourceCodeType.java             # 源语言变量类型 
│   ├── SymbolTableEntry.java           # 符号表条目 
│   └── SymbolTable.java                # 符号表 
├── utils                           # 杂项/工具 
│   ├── CasePaths.java                  # 记录程序中用到的相对于测试用例的各
类路径 
│   ├── FileUtils.java                  # 文件读写工具 
│   ├── IREmulator.java                 # 评测用 IR 解释器 
│   └── ListForwarder.java              # 辅助类, 用于便捷地为某个类实现 List 
接口 
├── Main.java                       # 主函数 
├── MainForAutoJudge.java           # 用于自动化评测的主函数 
└── NotImplementedException.java    # 用于填充待实现部分的异常 
```
