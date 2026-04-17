package cn.edu.hitsz.compiler.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 用于方便地做文件读写的工具类。
 */
public final class FileUtils {
    /**
     * 读取文本文件并以 String 形式返回文件内容。
     *
     * @param path 文本文件路径
     * @return 文本内容
     */
    public static String readFile(String path) {
        return String.join("\n", readLines(path));
    }

    /**
     * 读取文本文件并按行返回文件内容。
     *
     * @param path 文本文件路径
     * @return 文本内容
     */
    public static List<String> readLines(String path) {
        try (final var lines = Files.lines(Paths.get(path))) {
            return lines.toList();
        } catch (IOException e) {
            throw new RuntimeException("IO Exception on " + path, e);
        }
    }

    /**
     * 将内容写入指定文件。
     *
     * @param path    要写入的文件路径
     * @param content 要写入的内容
     */
    public static void writeFile(String path, String content) {
        writeLines(path, List.of(content));
    }

    public static void writeLines(String path, List<String> lines) {
        try {
            Files.write(Paths.get(path), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("IO Exception for " + path);
        }
    }

    /**
     * 创建空文件。
     *
     * @param path 文件路径
     */
    public static void tryCreateEmptyFile(String path) {
        try {
            Files.createFile(Paths.get(path));
        } catch (FileAlreadyExistsException e) {
            throw new RuntimeException("File already exist for " + path, e);
        } catch (IOException e) {
            throw new RuntimeException("IO Exception for " + path, e);
        }
    }

    /**
     * 读取 CSV 文件。
     * 支持双引号包裹的字段，因此能够正确解析像 "," 这样的终结符列名。
     *
     * @param path CSV 文件路径
     * @return 按行按列拆分后的结果
     */
    public static List<List<String>> readCSV(String path) {
        return readLines(path).stream()
            .map(FileUtils::parseCSVLine)
            .toList();
    }

    /**
     * 解析一行 CSV，支持双引号包裹的字段和转义双引号。
     *
     * @param line 一行 CSV 文本
     * @return 该行拆分后的字段列表
     */
    private static List<String> parseCSVLine(String line) {
        final var fields = new java.util.ArrayList<String>();
        final var builder = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            final char current = line.charAt(i);

            if (current == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    builder.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (current == ',' && !inQuotes) {
                fields.add(builder.toString());
                builder.setLength(0);
            } else {
                builder.append(current);
            }
        }

        fields.add(builder.toString());
        return fields;
    }

    private FileUtils() {
    }
}
