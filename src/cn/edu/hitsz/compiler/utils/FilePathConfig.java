package cn.edu.hitsz.compiler.utils;

/**
 * Program file paths.
 */
public final class FilePathConfig {
    //==================================== Input Files ========================================//
    /** Source code path. */
    public static final String SRC_CODE_PATH = "data/in/input_code.txt";

    /** Coding map path. */
    public static final String CODING_MAP_PATH = "data/in/coding_map.csv";

    /** Grammar file path. */
    public static final String GRAMMAR_PATH = "data/in/grammar.txt";

    /** LR parsing table path. */
    public static final String LR1_TABLE_PATH = "data/in/LR1_table.csv";

    //==================================== Output Files ========================================//
    /** Token output path. */
    public static final String TOKEN_PATH = "data/out/token.txt";

    /** Symbol table before semantic analysis. */
    public static final String OLD_SYMBOL_TABLE = "data/out/old_symbol_table.txt";

    /** Parser production list output path. */
    public static final String PARSER_PATH = "data/out/parser_list.txt";

    /** Symbol table after semantic analysis. */
    public static final String NEW_SYMBOL_TABLE = "data/out/new_symbol_table.txt";

    /** IR output path. */
    public static final String INTERMEDIATE_CODE_PATH = "data/out/intermediate_code.txt";

    /** IR emulate result path. */
    public static final String EMULATE_RESULT = "data/out/ir_emulate_result.txt";

    /** Assembly output path. */
    public static final String ASSEMBLY_LANGUAGE_PATH = "data/out/assembly_language.asm";

    private FilePathConfig() {
    }
}